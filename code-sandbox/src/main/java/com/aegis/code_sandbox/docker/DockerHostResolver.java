package com.aegis.code_sandbox.docker;

import com.aegis.code_sandbox.config.JudgeProperties;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns the operator-facing {@code JUDGE_DOCKER_HOST} / {@code JUDGE_DOCKER_SSH_*}
 * configuration into a validated connection plan.
 *
 * <p>Security rules enforced here:</p>
 * <ul>
 *   <li>Plain TCP is only allowed for an explicit loopback development daemon
 *       and only with {@code JUDGE_DOCKER_ALLOW_INSECURE_TCP=true}.</li>
 *   <li>Remote TCP requires mutual TLS with a readable
 *       {@code ca.pem}/{@code cert.pem}/{@code key.pem}.</li>
 *   <li>SSH requires a key or password and host-key verification by default.</li>
 * </ul>
 */
public final class DockerHostResolver {

    private static final String HOST_HELP =
            "Supported JUDGE_DOCKER_HOST values: unix:///var/run/docker.sock, "
                    + "npipe:////./pipe/docker_engine, ssh://user@host[:port], "
                    + "or tcp://host:2376 with JUDGE_DOCKER_TLS_VERIFY=1 and JUDGE_DOCKER_CERT_PATH set. "
                    + "Unauthenticated tcp://host:2375 is rejected.";

    private DockerHostResolver() {
    }

    public enum Kind {
        UNIX_SOCKET,
        NAMED_PIPE,
        TCP_TLS,
        TCP_INSECURE_LOOPBACK,
        SSH
    }

    public record SshTarget(
            String host,
            int port,
            String user,
            String keyPath,
            String keyPassphrase,
            String password,
            String knownHosts,
            boolean strictHostKeyChecking,
            String command
    ) {
    }

    public record Plan(
            Kind kind,
            URI dockerHost,
            String certPath,
            SshTarget sshTarget,
            String description,
            List<String> warnings
    ) {
    }

    public static Plan resolve(JudgeProperties properties) {
        JudgeProperties.Docker docker = properties.getDocker();
        List<String> warnings = new ArrayList<>();

        if (properties.isSshConfigured()) {
            if (docker.getHost() != null && !docker.getHost().isBlank()) {
                warnings.add("JUDGE_DOCKER_SSH_HOST is set, so JUDGE_DOCKER_HOST=" + redact(docker.getHost())
                        + " is ignored for the connection.");
            }
            SshTarget target = sshFromProperties(docker);
            return new Plan(Kind.SSH, null, null, target,
                    "ssh://" + target.user() + "@" + target.host() + ":" + target.port(), warnings);
        }

        String configuredHost = docker.getHost() == null ? "" : docker.getHost().trim();
        if (configuredHost.isEmpty()) {
            throw new IllegalArgumentException(
                    "JUDGE_DOCKER_HOST is not set. The Docker backend requires an explicit "
                            + "JUDGE_DOCKER_HOST or JUDGE_DOCKER_SSH_HOST; it no longer defaults to a local socket.");
        }

        URI uri;
        try {
            uri = URI.create(configuredHost);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("JUDGE_DOCKER_HOST is not a valid URI: " + configuredHost
                    + ". " + HOST_HELP, e);
        }

        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        return switch (scheme) {
            case "unix" -> unix(uri, configuredHost);
            case "npipe" -> namedPipe(uri, configuredHost);
            case "ssh" -> sshFromUri(uri, docker, warnings);
            case "tcp", "http", "https" -> tcp(uri, scheme, docker, warnings);
            default -> throw new IllegalArgumentException("Unsupported JUDGE_DOCKER_HOST scheme '" + scheme
                    + "' in " + redact(configuredHost) + ". " + HOST_HELP);
        };
    }

    private static Plan unix(URI uri, String configuredHost) {
        String path = uri.getPath();
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_HOST unix URI has no socket path: " + configuredHost);
        }
        return new Plan(Kind.UNIX_SOCKET, URI.create("unix://" + path), null, null,
                "unix://" + path, List.of());
    }

    private static Plan namedPipe(URI uri, String configuredHost) {
        if (uri.getPath() == null || uri.getPath().isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_HOST npipe URI has no pipe path: " + configuredHost);
        }
        if (!isWindows()) {
            throw new IllegalArgumentException(
                    "npipe:// is only available on Windows. On Linux use unix:// or ssh://.");
        }
        return new Plan(Kind.NAMED_PIPE, uri, null, null, redact(configuredHost), List.of());
    }

    private static Plan tcp(URI uri, String scheme, JudgeProperties.Docker docker, List<String> warnings) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_HOST TCP URI has no host: " + redact(uri.toString()));
        }

        boolean httpsScheme = "https".equals(scheme);
        boolean tls = httpsScheme || JudgeProperties.parseBoolean(docker.getTlsVerify());
        int port = uri.getPort() > 0 ? uri.getPort() : (tls ? 2376 : 2375);
        String dockerHost = "tcp://" + bracket(host) + ":" + port;

        if (tls) {
            String certPath = docker.getCertPath();
            requireCertificates(certPath);
            warnings.add("Docker daemon connection uses mutual TLS.");
            return new Plan(Kind.TCP_TLS, URI.create(dockerHost), certPath, null,
                    "tcp://" + host + ":" + port + " (mTLS)", warnings);
        }

        if (docker.getCertPath() != null && !docker.getCertPath().isBlank()) {
            throw new IllegalArgumentException(
                    "JUDGE_DOCKER_CERT_PATH is set but JUDGE_DOCKER_TLS_VERIFY is not enabled. "
                            + "Set JUDGE_DOCKER_TLS_VERIFY=1 (recommended) or remove JUDGE_DOCKER_CERT_PATH.");
        }
        if (!docker.isAllowInsecureTcp()) {
            throw new IllegalArgumentException(
                    "Refusing an unauthenticated Docker daemon at tcp://" + host + ":" + port + ". "
                            + "Use mTLS (JUDGE_DOCKER_TLS_VERIFY=1 + JUDGE_DOCKER_CERT_PATH), SSH "
                            + "(JUDGE_DOCKER_HOST=ssh://user@host), or a local unix socket. "
                            + "For loopback development only, set JUDGE_DOCKER_ALLOW_INSECURE_TCP=true.");
        }
        if (!isLoopback(host)) {
            throw new IllegalArgumentException(
                    "JUDGE_DOCKER_ALLOW_INSECURE_TCP only permits loopback daemons; '" + host
                            + "' is remote. Use mTLS or SSH.");
        }
        warnings.add("Using an unauthenticated loopback Docker daemon because "
                + "JUDGE_DOCKER_ALLOW_INSECURE_TCP=true. Never use this for a remote host.");
        return new Plan(Kind.TCP_INSECURE_LOOPBACK, URI.create(dockerHost), null, null,
                "tcp://" + host + ":" + port + " (insecure loopback)", warnings);
    }

    private static Plan sshFromUri(URI uri, JudgeProperties.Docker docker, List<String> warnings) {
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_HOST ssh URI has no host: " + redact(uri.toString()));
        }
        JudgeProperties.Ssh ssh = docker.getSsh();

        String uriUser = null;
        String uriPassword = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null && !userInfo.isBlank()) {
            int separator = userInfo.indexOf(':');
            if (separator >= 0) {
                uriUser = userInfo.substring(0, separator);
                uriPassword = userInfo.substring(separator + 1);
                warnings.add("A password in JUDGE_DOCKER_HOST is discouraged; prefer JUDGE_DOCKER_SSH_KEY_PATH.");
            } else {
                uriUser = userInfo;
            }
        }

        String user = firstNonBlank(uriUser, ssh.getUser());
        if (user == null) {
            throw new IllegalArgumentException(
                    "SSH Docker access requires a user. Use ssh://user@host or set JUDGE_DOCKER_SSH_USER.");
        }
        int port = uri.getPort() > 0 ? uri.getPort() : ssh.getPort();
        String password = firstNonBlank(uriPassword, ssh.getPassword());

        SshTarget target = validatedSshTarget(
                host, port, user, ssh.getKeyPath(), ssh.getKeyPassphrase(), password,
                ssh.getKnownHosts(), ssh.isStrictHostKeyChecking(), ssh.getDockerCommand());
        return new Plan(Kind.SSH, null, null, target,
                "ssh://" + user + "@" + host + ":" + port, warnings);
    }

    private static SshTarget sshFromProperties(JudgeProperties.Docker docker) {
        JudgeProperties.Ssh ssh = docker.getSsh();
        if (ssh.getUser() == null || ssh.getUser().isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_SSH_USER is required when JUDGE_DOCKER_SSH_HOST is set.");
        }
        if (ssh.getHost() == null || ssh.getHost().isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_SSH_HOST is blank.");
        }
        if (ssh.getPort() < 1 || ssh.getPort() > 65_535) {
            throw new IllegalArgumentException("JUDGE_DOCKER_SSH_PORT must be between 1 and 65535.");
        }
        return validatedSshTarget(
                ssh.getHost().trim(), ssh.getPort(), ssh.getUser().trim(),
                ssh.getKeyPath(), ssh.getKeyPassphrase(), ssh.getPassword(),
                ssh.getKnownHosts(), ssh.isStrictHostKeyChecking(), ssh.getDockerCommand());
    }

    private static SshTarget validatedSshTarget(
            String host,
            int port,
            String user,
            String keyPath,
            String keyPassphrase,
            String password,
            String knownHosts,
            boolean strictHostKeyChecking,
            String dockerCommand
    ) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException("SSH port must be between 1 and 65535.");
        }
        boolean hasKey = keyPath != null && !keyPath.isBlank();
        boolean hasPassword = password != null && !password.isBlank();
        if (!hasKey && !hasPassword) {
            throw new IllegalArgumentException(
                    "SSH Docker access requires JUDGE_DOCKER_SSH_KEY_PATH (recommended) or JUDGE_DOCKER_SSH_PASSWORD.");
        }
        if (hasKey) {
            Path key = Path.of(keyPath);
            if (!Files.isRegularFile(key) || !Files.isReadable(key)) {
                throw new IllegalArgumentException("JUDGE_DOCKER_SSH_KEY_PATH is not a readable file: " + keyPath);
            }
        }
        if (strictHostKeyChecking) {
            if (knownHosts == null || knownHosts.isBlank()) {
                throw new IllegalArgumentException(
                        "SSH host-key verification is enabled but JUDGE_DOCKER_SSH_KNOWN_HOSTS is not set. "
                                + "Upload a known_hosts file, or set "
                                + "JUDGE_DOCKER_SSH_STRICT_HOST_KEY_CHECKING=false for non-production testing.");
            }
            Path known = Path.of(knownHosts);
            if (!Files.isRegularFile(known) || !Files.isReadable(known)) {
                throw new IllegalArgumentException("JUDGE_DOCKER_SSH_KNOWN_HOSTS is not a readable file: " + knownHosts);
            }
        }
        if (dockerCommand == null || dockerCommand.isBlank()) {
            throw new IllegalArgumentException("JUDGE_DOCKER_SSH_DOCKER_COMMAND must not be blank.");
        }
        return new SshTarget(host, port, user, hasKey ? keyPath : null,
                keyPassphrase == null ? "" : keyPassphrase,
                hasPassword ? password : null,
                knownHosts == null ? "" : knownHosts,
                strictHostKeyChecking,
                dockerCommand.trim());
    }

    private static void requireCertificates(String certPath) {
        if (certPath == null || certPath.isBlank()) {
            throw new IllegalArgumentException(
                    "JUDGE_DOCKER_TLS_VERIFY is enabled but JUDGE_DOCKER_CERT_PATH is empty. "
                            + "Mount a directory containing ca.pem, cert.pem and key.pem.");
        }
        Path directory = Path.of(certPath);
        for (String fileName : List.of("ca.pem", "cert.pem", "key.pem")) {
            Path file = directory.resolve(fileName);
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
                throw new IllegalArgumentException(
                        "JUDGE_DOCKER_CERT_PATH must contain a readable " + fileName + " (looked in " + certPath + ").");
            }
        }
    }

    private static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost")
                || normalized.equals("::1")
                || normalized.equals("[::1]")
                || normalized.equals("127.0.0.1")
                || normalized.startsWith("127.");
    }

    private static String bracket(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return second == null || second.isBlank() ? null : second;
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String redact(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("://([^/@:]+):[^@/]+@", "://$1:***@");
    }
}
