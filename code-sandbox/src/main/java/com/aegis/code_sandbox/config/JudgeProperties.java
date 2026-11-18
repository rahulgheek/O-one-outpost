package com.aegis.code_sandbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Central configuration for the judge.
 *
 * <p>Environment variables use Spring Boot's relaxed names for the nested
 * properties (JUDGE_POOL_SIZE, JUDGE_EXECUTION_CPU_LIMIT, JUDGE_DOCKER_HOST,
 * ...). {@code application.yaml} maps the OnlineCompiler settings and the
 * user-facing time/memory limits (DEFAULT_TIME_LIMIT_MS, ...). Secrets (the
 * OnlineCompiler API key, SSH keys, certificate paths and passwords) come only
 * from the environment or mounted files and are never logged.</p>
 */
@ConfigurationProperties(prefix = "judge")
public class JudgeProperties {

    /** Master switch for code execution. */
    private boolean enabled = true;

    /**
     * Execution backend. {@code onlinecompiler} (default) sends submissions to
     * the OnlineCompiler.io HTTPS API. {@code docker} activates the warm
     * ContainerPool and the remote Docker Engine API.
     */
    private String backend = "onlinecompiler";

    /**
     * When true the API starts without executing code. Every execution request
     * fails closed with an INTERNAL_ERROR / SYSTEM_ERROR verdict.
     */
    private boolean apiOnlyMode = false;

    /**
     * Docker-only. When true, an unreachable Docker daemon aborts startup with
     * an actionable message. Set to false to start the API and let the pool
     * recover later.
     */
    private boolean requireDocker = true;

    /** Executor image. It must contain both java and javac. */
    private String image = "eclipse-temurin:17-alpine";

    /** if-missing | always | never */
    private String imagePullPolicy = "if-missing";

    /** Number of warm containers kept ready. */
    private int poolSize = 3;

    private final Docker docker = new Docker();
    private final OnlineCompiler onlineCompiler = new OnlineCompiler();
    private final Execution execution = new Execution();
    private final Pool pool = new Pool();

    /** True when either execution backend may run submissions. */
    public boolean isExecutionEnabled() {
        return enabled && !apiOnlyMode;
    }

    /** True only when the Docker pool is the selected, active backend. */
    public boolean isDockerEnabled() {
        return isExecutionEnabled() && isDockerBackend();
    }

    /** True only when the OnlineCompiler API is the selected, active backend. */
    public boolean isOnlineCompilerEnabled() {
        return isExecutionEnabled() && isOnlineCompilerBackend();
    }

    public boolean isDockerBackend() {
        return "docker".equals(normalizedBackend());
    }

    public boolean isOnlineCompilerBackend() {
        return "onlinecompiler".equals(normalizedBackend());
    }

    public boolean isOnlineCompilerConfigured() {
        return onlineCompiler.getApiKey() != null && !onlineCompiler.getApiKey().isBlank();
    }

    /**
     * Effective submission size limit for the selected backend. OnlineCompiler
     * caps a single source file at its own configured limit, so an oversized
     * submission must be rejected by request validation instead of being sent
     * to the provider and classified as a compilation error.
     */
    public int effectiveMaxCodeBytes() {
        int executionLimit = execution.getMaxCodeBytes();
        if (isOnlineCompilerBackend()) {
            return Math.min(executionLimit, onlineCompiler.getMaxCodeBytes());
        }
        return executionLimit;
    }

    public String normalizedBackend() {
        return backend == null ? "onlinecompiler" : backend.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isSshConfigured() {
        return docker.getSsh().getHost() != null && !docker.getSsh().getHost().isBlank();
    }

    public boolean isSshHost() {
        return docker.getHost() != null
                && docker.getHost().trim().toLowerCase(Locale.ROOT).startsWith("ssh://");
    }

    public boolean usesSsh() {
        return isSshConfigured() || isSshHost();
    }

    public boolean isTlsVerify() {
        return parseBoolean(docker.getTlsVerify());
    }

    /**
     * Validates numeric ranges and rejects unsafe combinations at startup.
     */
    public void validate() {
        String selected = normalizedBackend();
        if (!selected.equals("onlinecompiler") && !selected.equals("docker")) {
            throw new IllegalArgumentException(
                    "JUDGE_EXECUTION_BACKEND must be 'onlinecompiler' or 'docker', got '" + backend + "'");
        }

        execution.validate();

        if (isDockerBackend()) {
            validateDockerBackend();
        } else {
            onlineCompiler.validate(this);
        }
    }

    private void validateDockerBackend() {
        boolean hasDockerHost = docker.getHost() != null && !docker.getHost().isBlank();
        boolean hasSshHost = docker.getSsh().getHost() != null && !docker.getSsh().getHost().isBlank();
        if (isExecutionEnabled() && !hasDockerHost && !hasSshHost) {
            throw new IllegalArgumentException(
                    "JUDGE_DOCKER_HOST or JUDGE_DOCKER_SSH_HOST is required when "
                            + "JUDGE_EXECUTION_BACKEND=docker. The Docker backend no longer falls back to a "
                            + "local socket. Set an explicit Docker Engine endpoint, switch to "
                            + "JUDGE_EXECUTION_BACKEND=onlinecompiler, or set JUDGE_API_ONLY_MODE=true for an "
                            + "explicitly non-executing API.");
        }
        if (poolSize < 0 || poolSize > 64) {
            throw new IllegalArgumentException("judge.pool-size / JUDGE_POOL_SIZE must be between 0 and 64");
        }
        if (image == null || image.isBlank()) {
            throw new IllegalArgumentException("judge.image / JUDGE_IMAGE must not be blank");
        }
        String policy = normalizedImagePullPolicy();
        if (!policy.equals("if-missing") && !policy.equals("always") && !policy.equals("never")) {
            throw new IllegalArgumentException("judge.image-pull-policy must be if-missing, always or never");
        }
        if (pool.getStartupTimeoutMs() < 1_000) {
            throw new IllegalArgumentException("judge.startup-timeout-ms must be at least 1000");
        }
        if (pool.getCleanupTimeoutMs() < 100) {
            throw new IllegalArgumentException("judge.cleanup-timeout-ms must be at least 100");
        }
        if (pool.getBorrowTimeoutMs() < 1_000) {
            throw new IllegalArgumentException("judge.borrow-timeout-ms must be at least 1000");
        }
        if (pool.getContainerUser() == null || pool.getContainerUser().isBlank()) {
            throw new IllegalArgumentException("judge.container-user must not be blank");
        }
        if (pool.getWorkDir() == null || !pool.getWorkDir().startsWith("/")) {
            throw new IllegalArgumentException("judge.work-dir must be an absolute path");
        }
        if (pool.getPullTimeoutMs() < 1_000) {
            throw new IllegalArgumentException("judge.pull-timeout-ms must be at least 1000");
        }
        if (pool.getReconnectIntervalMs() < 1_000) {
            throw new IllegalArgumentException("judge.reconnect-interval-ms must be at least 1000");
        }
        if (pool.getHealthCheckTimeoutMs() < 500) {
            throw new IllegalArgumentException("judge.health-check-timeout-ms must be at least 500");
        }
        if (pool.getNamePrefix() == null || pool.getNamePrefix().isBlank()) {
            throw new IllegalArgumentException("judge.pool-name-prefix must not be blank");
        }
        docker.validate(this);
    }

    public String normalizedImagePullPolicy() {
        return imagePullPolicy == null ? "if-missing" : imagePullPolicy.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean parseBoolean(String value) {
        if (value == null) {
            return false;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        return v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("on");
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public Docker getDocker() {
        return docker;
    }

    public OnlineCompiler getOnlineCompiler() {
        return onlineCompiler;
    }

    public Execution getExecution() {
        return execution;
    }

    public Pool getPool() {
        return pool;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isApiOnlyMode() {
        return apiOnlyMode;
    }

    public void setApiOnlyMode(boolean apiOnlyMode) {
        this.apiOnlyMode = apiOnlyMode;
    }

    public boolean isRequireDocker() {
        return requireDocker;
    }

    public void setRequireDocker(boolean requireDocker) {
        this.requireDocker = requireDocker;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getImagePullPolicy() {
        return imagePullPolicy;
    }

    public void setImagePullPolicy(String imagePullPolicy) {
        this.imagePullPolicy = imagePullPolicy;
    }

    public int getPoolSize() {
        return poolSize;
    }

    public void setPoolSize(int poolSize) {
        this.poolSize = poolSize;
    }

    public static class Docker {
        /** JUDGE_DOCKER_HOST. tcp://host:2376, unix:///var/run/docker.sock or ssh://user@host */
        private String host = "";
        /** JUDGE_DOCKER_TLS_VERIFY. Accepts 1/true/yes. */
        private String tlsVerify = "";
        /** JUDGE_DOCKER_CERT_PATH containing ca.pem, cert.pem and key.pem. */
        private String certPath = "";
        /** Optional JUDGE_DOCKER_API_VERSION. */
        private String apiVersion = "";
        /** Explicit opt-in required for plain TCP daemons on loopback. */
        private boolean allowInsecureTcp = false;
        private long connectTimeoutMs = 5_000;
        private long responseTimeoutMs = 60_000;
        private final Ssh ssh = new Ssh();

        void validate(JudgeProperties parent) {
            if (connectTimeoutMs < 500) {
                throw new IllegalArgumentException("judge.docker.connect-timeout-ms must be at least 500");
            }
            if (responseTimeoutMs < 5_000) {
                throw new IllegalArgumentException("judge.docker.response-timeout-ms must be at least 5000");
            }
            if (parent.isSshConfigured()) {
                if (ssh.getUser() == null || ssh.getUser().isBlank()) {
                    throw new IllegalArgumentException("JUDGE_DOCKER_SSH_USER is required when JUDGE_DOCKER_SSH_HOST is set");
                }
                if (ssh.getPort() < 1 || ssh.getPort() > 65_535) {
                    throw new IllegalArgumentException("JUDGE_DOCKER_SSH_PORT is invalid");
                }
                if ((ssh.getKeyPath() == null || ssh.getKeyPath().isBlank())
                        && (ssh.getPassword() == null || ssh.getPassword().isBlank())) {
                    throw new IllegalArgumentException(
                            "SSH Docker access requires JUDGE_DOCKER_SSH_KEY_PATH (recommended) or JUDGE_DOCKER_SSH_PASSWORD");
                }
                if (!parent.isTlsVerify() && (host == null || host.isBlank())
                        && (ssh.getKnownHosts() == null || ssh.getKnownHosts().isBlank())
                        && ssh.isStrictHostKeyChecking()) {
                    throw new IllegalArgumentException(
                            "Strict SSH host-key checking is enabled but JUDGE_DOCKER_SSH_KNOWN_HOSTS is not set");
                }
            }
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getTlsVerify() {
            return tlsVerify;
        }

        public void setTlsVerify(String tlsVerify) {
            this.tlsVerify = tlsVerify;
        }

        public String getCertPath() {
            return certPath;
        }

        public void setCertPath(String certPath) {
            this.certPath = certPath;
        }

        public String getApiVersion() {
            return apiVersion;
        }

        public void setApiVersion(String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public boolean isAllowInsecureTcp() {
            return allowInsecureTcp;
        }

        public void setAllowInsecureTcp(boolean allowInsecureTcp) {
            this.allowInsecureTcp = allowInsecureTcp;
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getResponseTimeoutMs() {
            return responseTimeoutMs;
        }

        public void setResponseTimeoutMs(long responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
        }

        public Ssh getSsh() {
            return ssh;
        }
    }

    public static class Ssh {
        /** JUDGE_DOCKER_SSH_HOST (when JUDGE_DOCKER_HOST is not an ssh:// URI). */
        private String host = "";
        /** JUDGE_DOCKER_SSH_USER */
        private String user = "";
        /** JUDGE_DOCKER_SSH_PORT */
        private int port = 22;
        /** JUDGE_DOCKER_SSH_KEY_PATH */
        private String keyPath = "";
        /** JUDGE_DOCKER_SSH_KEY_PASSPHRASE */
        private String keyPassphrase = "";
        /** JUDGE_DOCKER_SSH_PASSWORD (not recommended; prefer keys). */
        private String password = "";
        /** JUDGE_DOCKER_SSH_KNOWN_HOSTS */
        private String knownHosts = "";
        /** JUDGE_DOCKER_SSH_STRICT_HOST_KEY_CHECKING */
        private boolean strictHostKeyChecking = true;
        /** JUDGE_DOCKER_SSH_DOCKER_COMMAND */
        private String dockerCommand = "docker system dial-stdio";

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getUser() {
            return user;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getKeyPath() {
            return keyPath;
        }

        public void setKeyPath(String keyPath) {
            this.keyPath = keyPath;
        }

        public String getKeyPassphrase() {
            return keyPassphrase;
        }

        public void setKeyPassphrase(String keyPassphrase) {
            this.keyPassphrase = keyPassphrase;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getKnownHosts() {
            return knownHosts;
        }

        public void setKnownHosts(String knownHosts) {
            this.knownHosts = knownHosts;
        }

        public boolean isStrictHostKeyChecking() {
            return strictHostKeyChecking;
        }

        public void setStrictHostKeyChecking(boolean strictHostKeyChecking) {
            this.strictHostKeyChecking = strictHostKeyChecking;
        }

        public String getDockerCommand() {
            return dockerCommand;
        }

        public void setDockerCommand(String dockerCommand) {
            this.dockerCommand = dockerCommand;
        }
    }

    /**
     * OnlineCompiler.io HTTP API configuration.
     *
     * <p>The API key is a server-side secret. It is never logged and must never
     * be exposed to the browser.</p>
     */
    public static class OnlineCompiler {
        /** ONLINECOMPILER_API_KEY. Raw value of the Authorization header. */
        private String apiKey = "";
        /** ONLINECOMPILER_BASE_URL. Must be HTTPS outside loopback tests. */
        private String baseUrl = "https://api.onlinecompiler.io";
        /** ONLINECOMPILER_COMPILER. openjdk-25 is OnlineCompiler's Java runtime. */
        private String compiler = "openjdk-25";
        /**
         * Optional per-language overrides, keyed by the judge language id
         * (java, python, cpp, c, javascript, typescript, go, rust). Values are
         * OnlineCompiler compiler ids. Empty means use the built-in default
         * from {@link com.aegis.code_sandbox.judge.Language}.
         */
        private final Map<String, String> compilers = new LinkedHashMap<>();
        private long connectTimeoutMs = 5_000;
        /** The provider's hard execution ceiling is 30s; leave HTTP headroom. */
        private long responseTimeoutMs = 45_000;
        /** Maximum time a request waits for a local concurrency slot. */
        private long queueTimeoutMs = 30_000;
        /** Provider Free/Starter allows 4 simultaneous runs. */
        private int maxConcurrency = 4;
        private int maxRetries = 2;
        private long retryBaseMs = 750;
        private long retryMaxMs = 5_000;
        /** OnlineCompiler accepts up to 100,000 encoded code bytes. */
        private int maxCodeBytes = 100_000;
        /** OnlineCompiler stdin ceiling is 5 MB. */
        private int maxInputBytes = 5_242_880;
        /**
         * Extra provider memory above the problem's limit. The provider counts
         * mapped JVM/runtime files in its peak figure, so plain Java already
         * reports roughly 230 MB. This is an explicit configuration value, not
         * an automatic host-load adjustment.
         */
        private int memoryHeadroomMb = 256;
        /** Provider plan ceiling; Free/Starter allow 512 MB. */
        private int maxMemoryMb = 512;
        /** Test-only escape hatch for a loopback mock server. */
        private boolean allowInsecureHttp = false;

        void validate(JudgeProperties parent) {
            if (parent.isOnlineCompilerEnabled() && !parent.isOnlineCompilerConfigured()) {
                throw new IllegalArgumentException(
                        "ONLINECOMPILER_API_KEY is required when JUDGE_EXECUTION_BACKEND=onlinecompiler. "
                                + "Set JUDGE_API_ONLY_MODE=true only for an explicitly non-executing API.");
            }
            if (compiler == null || compiler.isBlank()) {
                throw new IllegalArgumentException("ONLINECOMPILER_COMPILER must not be blank");
            }
            compilers.forEach((languageId, compilerId) -> {
                if (com.aegis.code_sandbox.judge.Language.find(languageId).isEmpty()) {
                    throw new IllegalArgumentException("Unknown language '" + languageId
                            + "' in ONLINECOMPILER_COMPILERS; supported: "
                            + com.aegis.code_sandbox.judge.Language.supportedIds());
                }
                if (compilerId == null || compilerId.isBlank()) {
                    throw new IllegalArgumentException("ONLINECOMPILER_COMPILERS_" + languageId
                            + " must not be blank");
                }
            });
            if (connectTimeoutMs < 100 || connectTimeoutMs > 60_000) {
                throw new IllegalArgumentException("ONLINECOMPILER_CONNECT_TIMEOUT_MS must be between 100 and 60000");
            }
            if (responseTimeoutMs < 1_000 || responseTimeoutMs > 120_000) {
                throw new IllegalArgumentException("ONLINECOMPILER_RESPONSE_TIMEOUT_MS must be between 1000 and 120000");
            }
            int maxRunMs = parent.getExecution().getMaxTimeLimitMs();
            if (maxRunMs > 30_000) {
                throw new IllegalArgumentException(
                        "MAX_TIME_LIMIT_MS must not exceed 30000 on the OnlineCompiler Free/Starter plan");
            }
            if (responseTimeoutMs < maxRunMs + 5_000L) {
                throw new IllegalArgumentException(
                        "ONLINECOMPILER_RESPONSE_TIMEOUT_MS must be at least MAX_TIME_LIMIT_MS + 5000");
            }
            if (queueTimeoutMs < 100 || queueTimeoutMs > 120_000) {
                throw new IllegalArgumentException("ONLINECOMPILER_QUEUE_TIMEOUT_MS must be between 100 and 120000");
            }
            if (maxConcurrency < 1 || maxConcurrency > 64) {
                throw new IllegalArgumentException("ONLINECOMPILER_MAX_CONCURRENCY must be between 1 and 64");
            }
            if (maxRetries < 0 || maxRetries > 5) {
                throw new IllegalArgumentException("ONLINECOMPILER_MAX_RETRIES must be between 0 and 5");
            }
            if (retryBaseMs < 1 || retryMaxMs < retryBaseMs) {
                throw new IllegalArgumentException(
                        "ONLINECOMPILER_RETRY_BASE_MS / ONLINECOMPILER_RETRY_MAX_MS are invalid");
            }
            if (maxCodeBytes < 1_024 || maxCodeBytes > 100_000) {
                throw new IllegalArgumentException("ONLINECOMPILER_MAX_CODE_BYTES must be between 1024 and 100000");
            }
            if (maxInputBytes < 0 || maxInputBytes > 5_242_880) {
                throw new IllegalArgumentException("ONLINECOMPILER_MAX_INPUT_BYTES must be between 0 and 5242880");
            }
            if (memoryHeadroomMb < 0 || memoryHeadroomMb > 2_048) {
                throw new IllegalArgumentException("ONLINECOMPILER_MEMORY_HEADROOM_MB must be between 0 and 2048");
            }
            if (maxMemoryMb < 16 || maxMemoryMb > 2_048) {
                throw new IllegalArgumentException("ONLINECOMPILER_MAX_MEMORY_MB must be between 16 and 2048");
            }
            validateBaseUrl();
        }

        private void validateBaseUrl() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("ONLINECOMPILER_BASE_URL must not be blank");
            }
            URI uri;
            try {
                uri = URI.create(baseUrl.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("ONLINECOMPILER_BASE_URL is not a valid URI: " + baseUrl);
            }
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                throw new IllegalArgumentException("ONLINECOMPILER_BASE_URL must include a host");
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return;
            }
            if ("http".equalsIgnoreCase(uri.getScheme())
                    && allowInsecureHttp
                    && isLoopbackHost(uri.getHost())) {
                return;
            }
            throw new IllegalArgumentException(
                    "ONLINECOMPILER_BASE_URL must use HTTPS. Plain HTTP is allowed only for loopback "
                            + "tests with ONLINECOMPILER_ALLOW_INSECURE_HTTP=true.");
        }

        private static boolean isLoopbackHost(String host) {
            if (host == null) {
                return false;
            }
            String normalized = host.toLowerCase(Locale.ROOT);
            return normalized.equals("localhost")
                    || normalized.equals("127.0.0.1")
                    || normalized.equals("::1")
                    || normalized.equals("[::1]");
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getCompiler() {
            return compiler;
        }

        public void setCompiler(String compiler) {
            this.compiler = compiler;
        }

        public Map<String, String> getCompilers() {
            return compilers;
        }

        /**
         * Resolves the provider compiler id for a language. Explicit
         * configuration wins; Java keeps honouring the legacy
         * ONLINECOMPILER_COMPILER value; everything else uses the registry
         * default.
         */
        public String compilerFor(com.aegis.code_sandbox.judge.Language language) {
            String configured = compilers.get(language.id());
            if (configured != null && !configured.isBlank()) {
                return configured.trim();
            }
            if (language == com.aegis.code_sandbox.judge.Language.JAVA
                    && compiler != null && !compiler.isBlank()) {
                return compiler.trim();
            }
            return language.defaultCompiler();
        }

        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public long getResponseTimeoutMs() {
            return responseTimeoutMs;
        }

        public void setResponseTimeoutMs(long responseTimeoutMs) {
            this.responseTimeoutMs = responseTimeoutMs;
        }

        public long getQueueTimeoutMs() {
            return queueTimeoutMs;
        }

        public void setQueueTimeoutMs(long queueTimeoutMs) {
            this.queueTimeoutMs = queueTimeoutMs;
        }

        public int getMaxConcurrency() {
            return maxConcurrency;
        }

        public void setMaxConcurrency(int maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public long getRetryBaseMs() {
            return retryBaseMs;
        }

        public void setRetryBaseMs(long retryBaseMs) {
            this.retryBaseMs = retryBaseMs;
        }

        public long getRetryMaxMs() {
            return retryMaxMs;
        }

        public void setRetryMaxMs(long retryMaxMs) {
            this.retryMaxMs = retryMaxMs;
        }

        public int getMaxCodeBytes() {
            return maxCodeBytes;
        }

        public void setMaxCodeBytes(int maxCodeBytes) {
            this.maxCodeBytes = maxCodeBytes;
        }

        public int getMaxInputBytes() {
            return maxInputBytes;
        }

        public void setMaxInputBytes(int maxInputBytes) {
            this.maxInputBytes = maxInputBytes;
        }

        public int getMemoryHeadroomMb() {
            return memoryHeadroomMb;
        }

        public void setMemoryHeadroomMb(int memoryHeadroomMb) {
            this.memoryHeadroomMb = memoryHeadroomMb;
        }

        public int getMaxMemoryMb() {
            return maxMemoryMb;
        }

        public void setMaxMemoryMb(int maxMemoryMb) {
            this.maxMemoryMb = maxMemoryMb;
        }

        public boolean isAllowInsecureHttp() {
            return allowInsecureHttp;
        }

        public void setAllowInsecureHttp(boolean allowInsecureHttp) {
            this.allowInsecureHttp = allowInsecureHttp;
        }
    }

    public static class Execution {
        /** DEFAULT_TIME_LIMIT_MS */
        private int defaultTimeLimitMs = 2_000;
        /** MAX_TIME_LIMIT_MS */
        private int maxTimeLimitMs = 10_000;
        /** DEFAULT_MEMORY_LIMIT_MB */
        private int defaultMemoryLimitMb = 256;
        /** MAX_MEMORY_LIMIT_MB */
        private int maxMemoryLimitMb = 512;
        private int minMemoryLimitMb = 64;
        /**
         * Explicit, operator-controlled calibration factor. It is never changed
         * automatically based on host load. 1.0 = reference environment.
         */
        private double timeLimitMultiplier = 1.0;
        /** Additional wall-clock allowance before the container is killed. */
        private long wallClockGraceMs = 500;
        /** Classification tolerance for wall/CPU time. */
        private long timeLimitToleranceMs = 50;
        /** Compilation is not charged to the contestant's execution time. */
        private long compileTimeoutMs = 10_000;
        /** Bounded time for writing the submitted source into the tmpfs. */
        private long sourceWriteTimeoutMs = 10_000;
        private int compileMemoryMb = 256;
        /** Extra memory allowed for JVM/native overhead above the heap limit. */
        private int memoryOverheadMb = 64;
        private double cpuLimit = 0.5;
        private long pidsLimit = 64;
        private int maxOutputBytes = 1_048_576;
        private int maxCodeBytes = 262_144;
        /** Keeps JVM thread pools/GC deterministic across servers. */
        private int activeProcessorCount = 1;
        /** Comma-separated extra JVM flags passed to user code. */
        private String extraJvmArgs = "";
        private int workDirSizeMb = 64;
        private int tmpDirSizeMb = 16;
        private int shmSizeMb = 16;

        void validate() {
            if (defaultTimeLimitMs < 1 || maxTimeLimitMs < defaultTimeLimitMs || maxTimeLimitMs > 600_000) {
                throw new IllegalArgumentException(
                        "DEFAULT_TIME_LIMIT_MS / MAX_TIME_LIMIT_MS are invalid (max must be >= default)");
            }
            if (defaultMemoryLimitMb < 16 || maxMemoryLimitMb < defaultMemoryLimitMb
                    || minMemoryLimitMb > defaultMemoryLimitMb) {
                throw new IllegalArgumentException(
                        "DEFAULT_MEMORY_LIMIT_MB / MAX_MEMORY_LIMIT_MB / judge.min-memory-limit-mb are invalid");
            }
            if (memoryOverheadMb < 0 || memoryOverheadMb > maxMemoryLimitMb) {
                throw new IllegalArgumentException("judge.memory-overhead-mb must be between 0 and MAX_MEMORY_LIMIT_MB");
            }
            if (compileMemoryMb < 64 || compileMemoryMb > 8192) {
                throw new IllegalArgumentException("judge.compile-memory-mb must be between 64 and 8192");
            }
            if (cpuLimit <= 0 || cpuLimit > 64) {
                throw new IllegalArgumentException("JUDGE_EXECUTION_CPU_LIMIT must be greater than 0 and at most 64");
            }
            if (pidsLimit < 8 || pidsLimit > 4096) {
                throw new IllegalArgumentException("JUDGE_EXECUTION_PIDS_LIMIT must be between 8 and 4096");
            }
            if (maxOutputBytes < 4096 || maxOutputBytes > 16 * 1024 * 1024) {
                throw new IllegalArgumentException("judge.max-output-bytes must be between 4096 and 16777216");
            }
            if (maxCodeBytes < 1024 || maxCodeBytes > 4 * 1024 * 1024) {
                throw new IllegalArgumentException("judge.max-code-bytes must be between 1024 and 4194304");
            }
            if (compileTimeoutMs < 1_000 || compileTimeoutMs > 300_000) {
                throw new IllegalArgumentException("judge.compile-timeout-ms must be between 1000 and 300000");
            }
            if (sourceWriteTimeoutMs < 1_000 || sourceWriteTimeoutMs > 120_000) {
                throw new IllegalArgumentException(
                        "JUDGE_EXECUTION_SOURCE_WRITE_TIMEOUT_MS must be between 1000 and 120000");
            }
            if (timeLimitMultiplier <= 0 || timeLimitMultiplier > 10) {
                throw new IllegalArgumentException("JUDGE_TIME_LIMIT_MULTIPLIER must be greater than 0 and at most 10");
            }
            if (wallClockGraceMs < 0 || wallClockGraceMs > 60_000) {
                throw new IllegalArgumentException("judge.wall-clock-grace-ms must be between 0 and 60000");
            }
            if (timeLimitToleranceMs < 0 || timeLimitToleranceMs > 10_000) {
                throw new IllegalArgumentException("judge.time-limit-tolerance-ms must be between 0 and 10000");
            }
            if (activeProcessorCount < 1 || activeProcessorCount > 256) {
                throw new IllegalArgumentException("JUDGE_EXECUTION_ACTIVE_PROCESSOR_COUNT must be between 1 and 256");
            }
            if (workDirSizeMb < 8 || tmpDirSizeMb < 1 || shmSizeMb < 1) {
                throw new IllegalArgumentException("Temporary filesystem sizes are too small");
            }
        }

        public int getDefaultTimeLimitMs() {
            return defaultTimeLimitMs;
        }

        public void setDefaultTimeLimitMs(int defaultTimeLimitMs) {
            this.defaultTimeLimitMs = defaultTimeLimitMs;
        }

        public int getMaxTimeLimitMs() {
            return maxTimeLimitMs;
        }

        public void setMaxTimeLimitMs(int maxTimeLimitMs) {
            this.maxTimeLimitMs = maxTimeLimitMs;
        }

        public int getDefaultMemoryLimitMb() {
            return defaultMemoryLimitMb;
        }

        public void setDefaultMemoryLimitMb(int defaultMemoryLimitMb) {
            this.defaultMemoryLimitMb = defaultMemoryLimitMb;
        }

        public int getMaxMemoryLimitMb() {
            return maxMemoryLimitMb;
        }

        public void setMaxMemoryLimitMb(int maxMemoryLimitMb) {
            this.maxMemoryLimitMb = maxMemoryLimitMb;
        }

        public int getMinMemoryLimitMb() {
            return minMemoryLimitMb;
        }

        public void setMinMemoryLimitMb(int minMemoryLimitMb) {
            this.minMemoryLimitMb = minMemoryLimitMb;
        }

        public double getTimeLimitMultiplier() {
            return timeLimitMultiplier;
        }

        public void setTimeLimitMultiplier(double timeLimitMultiplier) {
            this.timeLimitMultiplier = timeLimitMultiplier;
        }

        public long getWallClockGraceMs() {
            return wallClockGraceMs;
        }

        public void setWallClockGraceMs(long wallClockGraceMs) {
            this.wallClockGraceMs = wallClockGraceMs;
        }

        public long getTimeLimitToleranceMs() {
            return timeLimitToleranceMs;
        }

        public void setTimeLimitToleranceMs(long timeLimitToleranceMs) {
            this.timeLimitToleranceMs = timeLimitToleranceMs;
        }

        public long getCompileTimeoutMs() {
            return compileTimeoutMs;
        }

        public void setCompileTimeoutMs(long compileTimeoutMs) {
            this.compileTimeoutMs = compileTimeoutMs;
        }

        public long getSourceWriteTimeoutMs() {
            return sourceWriteTimeoutMs;
        }

        public void setSourceWriteTimeoutMs(long sourceWriteTimeoutMs) {
            this.sourceWriteTimeoutMs = sourceWriteTimeoutMs;
        }

        public int getCompileMemoryMb() {
            return compileMemoryMb;
        }

        public void setCompileMemoryMb(int compileMemoryMb) {
            this.compileMemoryMb = compileMemoryMb;
        }

        public int getMemoryOverheadMb() {
            return memoryOverheadMb;
        }

        public void setMemoryOverheadMb(int memoryOverheadMb) {
            this.memoryOverheadMb = memoryOverheadMb;
        }

        public double getCpuLimit() {
            return cpuLimit;
        }

        public void setCpuLimit(double cpuLimit) {
            this.cpuLimit = cpuLimit;
        }

        public long getPidsLimit() {
            return pidsLimit;
        }

        public void setPidsLimit(long pidsLimit) {
            this.pidsLimit = pidsLimit;
        }

        public int getMaxOutputBytes() {
            return maxOutputBytes;
        }

        public void setMaxOutputBytes(int maxOutputBytes) {
            this.maxOutputBytes = maxOutputBytes;
        }

        public int getMaxCodeBytes() {
            return maxCodeBytes;
        }

        public void setMaxCodeBytes(int maxCodeBytes) {
            this.maxCodeBytes = maxCodeBytes;
        }

        public int getActiveProcessorCount() {
            return activeProcessorCount;
        }

        public void setActiveProcessorCount(int activeProcessorCount) {
            this.activeProcessorCount = activeProcessorCount;
        }

        public String getExtraJvmArgs() {
            return extraJvmArgs;
        }

        public void setExtraJvmArgs(String extraJvmArgs) {
            this.extraJvmArgs = extraJvmArgs;
        }

        public int getWorkDirSizeMb() {
            return workDirSizeMb;
        }

        public void setWorkDirSizeMb(int workDirSizeMb) {
            this.workDirSizeMb = workDirSizeMb;
        }

        public int getTmpDirSizeMb() {
            return tmpDirSizeMb;
        }

        public void setTmpDirSizeMb(int tmpDirSizeMb) {
            this.tmpDirSizeMb = tmpDirSizeMb;
        }

        public int getShmSizeMb() {
            return shmSizeMb;
        }

        public void setShmSizeMb(int shmSizeMb) {
            this.shmSizeMb = shmSizeMb;
        }
    }

    public static class Pool {
        private long startupTimeoutMs = 10_000;
        private long cleanupTimeoutMs = 3_000;
        private long borrowTimeoutMs = 10_000;
        private boolean warmupOnStartup = true;
        private boolean warmupBlocking = true;
        private long reconnectIntervalMs = 5_000;
        private long refillRetryBaseMs = 1_000;
        private long refillRetryMaxMs = 30_000;
        private long pullTimeoutMs = 600_000;
        private long healthCheckTimeoutMs = 5_000;
        private String containerUser = "10001:10001";
        private String workDir = "/app";
        private String namePrefix = "algoarena-warm";

        public long getStartupTimeoutMs() {
            return startupTimeoutMs;
        }

        public void setStartupTimeoutMs(long startupTimeoutMs) {
            this.startupTimeoutMs = startupTimeoutMs;
        }

        public long getCleanupTimeoutMs() {
            return cleanupTimeoutMs;
        }

        public void setCleanupTimeoutMs(long cleanupTimeoutMs) {
            this.cleanupTimeoutMs = cleanupTimeoutMs;
        }

        public long getBorrowTimeoutMs() {
            return borrowTimeoutMs;
        }

        public void setBorrowTimeoutMs(long borrowTimeoutMs) {
            this.borrowTimeoutMs = borrowTimeoutMs;
        }

        public boolean isWarmupOnStartup() {
            return warmupOnStartup;
        }

        public void setWarmupOnStartup(boolean warmupOnStartup) {
            this.warmupOnStartup = warmupOnStartup;
        }

        public boolean isWarmupBlocking() {
            return warmupBlocking;
        }

        public void setWarmupBlocking(boolean warmupBlocking) {
            this.warmupBlocking = warmupBlocking;
        }

        public long getReconnectIntervalMs() {
            return reconnectIntervalMs;
        }

        public void setReconnectIntervalMs(long reconnectIntervalMs) {
            this.reconnectIntervalMs = reconnectIntervalMs;
        }

        public long getRefillRetryBaseMs() {
            return refillRetryBaseMs;
        }

        public void setRefillRetryBaseMs(long refillRetryBaseMs) {
            this.refillRetryBaseMs = refillRetryBaseMs;
        }

        public long getRefillRetryMaxMs() {
            return refillRetryMaxMs;
        }

        public void setRefillRetryMaxMs(long refillRetryMaxMs) {
            this.refillRetryMaxMs = refillRetryMaxMs;
        }

        public long getPullTimeoutMs() {
            return pullTimeoutMs;
        }

        public void setPullTimeoutMs(long pullTimeoutMs) {
            this.pullTimeoutMs = pullTimeoutMs;
        }

        public long getHealthCheckTimeoutMs() {
            return healthCheckTimeoutMs;
        }

        public void setHealthCheckTimeoutMs(long healthCheckTimeoutMs) {
            this.healthCheckTimeoutMs = healthCheckTimeoutMs;
        }

        public String getContainerUser() {
            return containerUser;
        }

        public void setContainerUser(String containerUser) {
            this.containerUser = containerUser;
        }

        public String getWorkDir() {
            return workDir;
        }

        public void setWorkDir(String workDir) {
            this.workDir = workDir;
        }

        public String getNamePrefix() {
            return namePrefix;
        }

        public void setNamePrefix(String namePrefix) {
            this.namePrefix = namePrefix;
        }
    }
}
