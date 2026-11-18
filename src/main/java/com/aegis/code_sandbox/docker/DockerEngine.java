package com.aegis.code_sandbox.docker;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.exception.DockerUnavailableException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.LocalDirectorySSLConfig;
import com.github.dockerjava.transport.SSLConfig;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Owns the single Docker Java client used by the judge.
 *
 * <p>No Docker CLI is invoked. The client speaks the Docker Engine HTTP API
 * over a unix socket, a Windows named pipe, mutual TLS TCP, or an SSH tunnel
 * backed by {@code docker system dial-stdio}.</p>
 */
@Component
public class DockerEngine implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DockerEngine.class);

    private final JudgeProperties properties;
    private final Object lock = new Object();
    private final AtomicBoolean closed = new AtomicBoolean();

    private volatile DockerClient client;
    private volatile SshDockerTunnel tunnel;
    private volatile String endpointDescription = "not connected";

    public DockerEngine(JudgeProperties properties) {
        this.properties = properties;
    }

    /**
     * Returns the connected client, dialing/re-dialing if necessary.
     *
     * @throws DockerUnavailableException when the engine cannot be reached
     */
    public DockerClient getClient() {
        DockerClient current = client;
        if (current != null && !closed.get()) {
            return current;
        }
        synchronized (lock) {
            if (closed.get()) {
                throw new DockerUnavailableException("Docker client is closed; the sandbox is shutting down.");
            }
            if (client != null) {
                return client;
            }
            return connectLocked();
        }
    }

    /**
     * Called during startup. With {@code JUDGE_REQUIRE_DOCKER=true} (default)
     * a failure aborts application startup with an actionable message.
     */
    public void verifyOnStartup() {
        if (!properties.isDockerEnabled()) {
            log.info("Docker startup check skipped (backend={}, enabled={}, api-only={}).",
                    properties.normalizedBackend(), properties.isEnabled(), properties.isApiOnlyMode());
            return;
        }
        try {
            getClient();
        } catch (DockerUnavailableException e) {
            if (properties.isRequireDocker()) {
                throw e;
            }
            log.error("Docker is unavailable; starting in degraded mode because "
                    + "JUDGE_REQUIRE_DOCKER=false. Execution requests will fail closed until it reconnects. {}",
                    e.getMessage());
        }
    }

    /**
     * Lightweight liveness check used by the pool watchdog.
     */
    public boolean isConnected() {
        DockerClient current = client;
        if (current == null) {
            return false;
        }
        try {
            current.pingCmd().exec();
            return true;
        } catch (Exception e) {
            log.warn("Docker ping failed: {}", ContainerExec.rootMessage(e));
            return false;
        }
    }

    public String getEndpointDescription() {
        return endpointDescription;
    }

    private DockerClient connectLocked() {
        DockerHostResolver.Plan plan;
        try {
            plan = DockerHostResolver.resolve(properties);
        } catch (RuntimeException e) {
            throw new DockerUnavailableException(
                    "Docker connection configuration is invalid: " + e.getMessage(), e);
        }

        for (String warning : plan.warnings()) {
            log.warn(warning);
        }

        SshDockerTunnel newTunnel = null;
        ZerodepDockerHttpClient httpClient = null;
        DockerClient newClient = null;
        String description = plan.description();
        try {
            URI dockerHost;
            SSLConfig sslConfig = null;

            if (plan.kind() == DockerHostResolver.Kind.SSH) {
                newTunnel = new SshDockerTunnel(plan.sshTarget(), properties.getDocker().getConnectTimeoutMs());
                newTunnel.start();
                dockerHost = URI.create(newTunnel.dockerHostUri());
                description = plan.description() + " via local tunnel " + dockerHost;
            } else {
                dockerHost = plan.dockerHost();
                if (plan.certPath() != null && !plan.certPath().isBlank()) {
                    sslConfig = new LocalDirectorySSLConfig(plan.certPath());
                }
            }

            DefaultDockerClientConfig.Builder configBuilder =
                    DefaultDockerClientConfig.createDefaultConfigBuilder()
                            .withDockerHost(dockerHost.toString())
                            .withDockerTlsVerify(sslConfig != null);
            String apiVersion = properties.getDocker().getApiVersion();
            if (apiVersion != null && !apiVersion.isBlank()) {
                configBuilder.withApiVersion(apiVersion.trim());
            }
            if (sslConfig instanceof LocalDirectorySSLConfig localSsl) {
                configBuilder.withCustomSslConfig(localSsl);
            }
            DockerClientConfig config = configBuilder.build();

            httpClient = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(dockerHost)
                    .sslConfig(sslConfig)
                    .maxConnections(Math.max(16, (properties.getPoolSize() * 4) + 8))
                    .connectionTimeout(Duration.ofMillis(properties.getDocker().getConnectTimeoutMs()))
                    .responseTimeout(Duration.ofMillis(properties.getDocker().getResponseTimeoutMs()))
                    .build();

            newClient = DockerClientImpl.getInstance(config, httpClient);
            newClient.pingCmd().exec();

            this.client = newClient;
            this.tunnel = newTunnel;
            this.endpointDescription = description;
            log.info("Connected to Docker Engine at {} (API {}, image {}, pool size {})",
                    description,
                    apiVersion == null || apiVersion.isBlank() ? "auto" : apiVersion,
                    properties.getImage(),
                    properties.getPoolSize());
            return newClient;
        } catch (Exception e) {
            closeQuietly(newClient);
            closeQuietly(httpClient);
            if (newTunnel != null) {
                newTunnel.close();
            }
            throw new DockerUnavailableException(
                    "Unable to connect to Docker Engine at " + description + ". "
                            + ContainerExec.rootMessage(e) + ". "
                            + "Check JUDGE_DOCKER_HOST, JUDGE_DOCKER_TLS_VERIFY, JUDGE_DOCKER_CERT_PATH, or the JUDGE_DOCKER_SSH_* settings. "
                            + "The daemon must be reachable from this application container; no Docker CLI is installed. "
                            + "Use SSH or mutual TLS for remote daemons. For API-only deployments set "
                            + "JUDGE_API_ONLY_MODE=true explicitly.",
                    e);
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            closeQuietly(client);
            client = null;
            if (tunnel != null) {
                tunnel.close();
                tunnel = null;
            }
            endpointDescription = "closed";
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
}
