package com.aegis.code_sandbox.docker;

import com.aegis.code_sandbox.util.BoundedOutputStream;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bridges Docker's HTTP API over SSH.
 *
 * <p>For every accepted loopback TCP connection we open a dedicated SSH exec
 * channel running {@code docker system dial-stdio}. One channel per connection
 * is important: HTTP keep-alive/pipelining must not interleave bytes from
 * separate Docker API requests on a shared channel.</p>
 *
 * <p>The local listener is bound to {@code 127.0.0.1:0} (ephemeral port) and is
 * never exposed outside the JVM host. The Docker Java client connects to that
 * loopback endpoint.</p>
 */
public final class SshDockerTunnel implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(SshDockerTunnel.class);

    private final DockerHostResolver.SshTarget target;
    private final long connectTimeoutMs;
    private final ExecutorService ioExecutor;
    private final Set<Socket> clients = ConcurrentHashMap.newKeySet();
    private final Set<ChannelExec> channels = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean closed = new AtomicBoolean();

    private Session session;
    private ServerSocket serverSocket;

    public SshDockerTunnel(DockerHostResolver.SshTarget target, long connectTimeoutMs) {
        this.target = target;
        this.connectTimeoutMs = connectTimeoutMs;
        this.ioExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
            private int counter;

            @Override
            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "sandbox-ssh-" + (++counter));
                thread.setDaemon(true);
                return thread;
            }
        });
    }

    public synchronized void start() {
        if (serverSocket != null) {
            return;
        }
        try {
            JSch jsch = new JSch();
            if (target.keyPath() != null && !target.keyPath().isBlank()) {
                String passphrase = target.keyPassphrase() == null || target.keyPassphrase().isBlank()
                        ? null
                        : target.keyPassphrase();
                jsch.addIdentity(target.keyPath(), passphrase);
            }
            if (target.knownHosts() != null && !target.knownHosts().isBlank()) {
                jsch.setKnownHosts(target.knownHosts());
            }

            session = jsch.getSession(target.user(), target.host(), target.port());
            if (target.password() != null && !target.password().isBlank()) {
                session.setPassword(target.password());
            }
            session.setConfig("StrictHostKeyChecking", target.strictHostKeyChecking() ? "yes" : "no");
            session.setServerAliveInterval(15_000);
            session.setServerAliveCountMax(3);
            session.connect((int) Math.min(Integer.MAX_VALUE, Math.max(1_000, connectTimeoutMs)));

            serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 64);

            ioExecutor.submit(this::acceptLoop);
            log.info("SSH Docker tunnel ready: {}:{} (local 127.0.0.1:{})",
                    target.host(), target.port(), serverSocket.getLocalPort());
        } catch (Exception e) {
            close();
            throw new IllegalStateException("Could not open SSH tunnel to " + target.user() + "@"
                    + target.host() + ":" + target.port() + ": " + rootMessage(e), e);
        }
    }

    public String dockerHostUri() {
        if (serverSocket == null) {
            throw new IllegalStateException("SSH tunnel is not started");
        }
        return "tcp://127.0.0.1:" + serverSocket.getLocalPort();
    }

    private void acceptLoop() {
        while (!closed.get()) {
            try {
                Socket client = serverSocket.accept();
                client.setTcpNoDelay(true);
                client.setKeepAlive(true);
                clients.add(client);
                ioExecutor.submit(() -> handle(client));
            } catch (IOException e) {
                if (!closed.get()) {
                    log.warn("SSH tunnel accept failed: {}", rootMessage(e));
                }
                return;
            }
        }
    }

    private void handle(Socket client) {
        ChannelExec channel = null;
        BoundedOutputStream errorOutput = new BoundedOutputStream(4_096);
        try {
            channel = (ChannelExec) session.openChannel("exec");
            channels.add(channel);
            channel.setCommand(target.command());
            channel.setPty(false);
            channel.setInputStream(null);
            channel.setErrStream(errorOutput);
            InputStream remoteInput = channel.getInputStream();
            OutputStream remoteOutput = channel.getOutputStream();
            channel.connect((int) Math.min(Integer.MAX_VALUE, Math.max(1_000, connectTimeoutMs)));

            ChannelExec activeChannel = channel;
            Thread up = pump("sandbox-ssh-up", client.getInputStream(), remoteOutput, client, activeChannel);
            Thread down = pump("sandbox-ssh-down", remoteInput, client.getOutputStream(), client, activeChannel);
            up.start();
            down.start();
            up.join();
            down.join();
        } catch (Exception e) {
            if (!closed.get()) {
                log.debug("SSH tunnel connection ended: {}", rootMessage(e));
            }
        } finally {
            String remoteError = errorOutput.text().trim();
            if (!remoteError.isEmpty() && !closed.get()) {
                log.warn("Docker SSH helper reported: {}", remoteError);
            }
            closeQuietly(client);
            clients.remove(client);
            if (channel != null) {
                channels.remove(channel);
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private Thread pump(String name, InputStream input, OutputStream output, Socket client, ChannelExec channel) {
        Thread thread = new Thread(() -> {
            byte[] buffer = new byte[32 * 1024];
            try {
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    output.write(buffer, 0, read);
                    output.flush();
                }
            } catch (IOException e) {
                // Socket/channel close is the normal shutdown path.
            } finally {
                // Closing both ends unblocks the sibling pump. The Docker HTTP
                // client does not rely on half-close semantics.
                closeQuietly(client);
                try {
                    channel.disconnect();
                } catch (Exception ignored) {
                }
            }
        }, name);
        thread.setDaemon(true);
        return thread;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        closeQuietly(serverSocket);
        for (Socket client : clients) {
            closeQuietly(client);
        }
        clients.clear();
        for (ChannelExec channel : channels) {
            try {
                channel.disconnect();
            } catch (Exception ignored) {
            }
        }
        channels.clear();
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignored) {
            }
        }
        ioExecutor.shutdownNow();
        try {
            ioExecutor.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
