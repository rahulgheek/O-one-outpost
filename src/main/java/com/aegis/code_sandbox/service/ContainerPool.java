package com.aegis.code_sandbox.service;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.docker.ContainerExec;
import com.aegis.code_sandbox.docker.DockerEngine;
import com.aegis.code_sandbox.exception.DockerUnavailableException;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.core.command.PullImageResultCallback;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Warm pool of isolated Java executor containers.
 *
 * <p>Containers are created through the Docker Engine API. A container is used
 * for exactly one submission, then destroyed; class files and user state can
 * never leak into another submission.</p>
 */
@Component
public class ContainerPool {

    private static final Logger log = LoggerFactory.getLogger(ContainerPool.class);

    public static final String LABEL_MANAGED = "algoarena.sandbox.managed";
    public static final String LABEL_OWNER = "algoarena.sandbox.owner";
    public static final String LABEL_CREATED = "algoarena.sandbox.created";

    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final long CPU_PERIOD = 100_000L;

    private final JudgeProperties properties;
    private final DockerEngine dockerEngine;
    private final String ownerId;

    private final BlockingQueue<WarmContainer> available = new LinkedBlockingQueue<>();
    private final Set<WarmContainer> all = ConcurrentHashMap.newKeySet();
    private final Set<WarmContainer> pendingRemoval = ConcurrentHashMap.newKeySet();

    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean shutdownStarted = new AtomicBoolean();
    private final AtomicBoolean refilling = new AtomicBoolean();
    private final AtomicInteger refillFailures = new AtomicInteger();
    private final AtomicLong nextRefillAt = new AtomicLong();
    private final Object imageLock = new Object();

    private final ExecutorService refillExecutor =
            Executors.newSingleThreadExecutor(daemonFactory("sandbox-pool-refill"));
    private final ExecutorService cleanupExecutor =
            Executors.newCachedThreadPool(daemonFactory("sandbox-pool-cleanup"));

    private volatile ScheduledExecutorService watchdog;
    private volatile boolean imageReady;

    public ContainerPool(
            JudgeProperties properties,
            DockerEngine dockerEngine,
            @Value("${server.port:8085}") int serverPort
    ) {
        this.properties = properties;
        this.dockerEngine = dockerEngine;
        this.ownerId = hostName() + "-" + serverPort;
    }

    public record WarmContainer(String id, String name, long createdAtEpochMs) {
    }

    public record PoolStatus(
            int desiredSize,
            int available,
            int total,
            int pendingRemoval,
            boolean dockerConnected,
            String endpoint,
            String image,
            boolean imageReady
    ) {
    }

    @PostConstruct
    public void init() {
        properties.validate();
        if (!properties.isDockerEnabled()) {
            log.info("Docker container pool is inactive (backend={}, enabled={}, api-only={}). "
                            + "The OnlineCompiler backend does not require Docker.",
                    properties.normalizedBackend(), properties.isEnabled(), properties.isApiOnlyMode());
            return;
        }

        running.set(true);
        try {
            dockerEngine.verifyOnStartup();
            if (!dockerEngine.isConnected()) {
                log.error("Container pool will wait for Docker to become reachable at {}.",
                        dockerEngine.getEndpointDescription());
                startWatchdog();
                return;
            }

            ensureImage();
            cleanupStaleContainers();

            if (properties.getPool().isWarmupOnStartup()) {
                if (properties.getPool().isWarmupBlocking()) {
                    warmupBlocking();
                } else {
                    refillAsync();
                }
            }
            startWatchdog();
            log.info("Container pool ready: {} warm container(s), image={}", available.size(), properties.getImage());
        } catch (RuntimeException e) {
            if (properties.isRequireDocker()) {
                running.set(false);
                throw e;
            }
            log.error("Container pool startup failed; continuing in degraded mode because "
                    + "JUDGE_REQUIRE_DOCKER=false: {}", e.getMessage());
            startWatchdog();
        }
    }

    /**
     * Borrows a healthy warm container, waiting up to {@code borrowTimeoutMs}.
     * The container is not returned to the pool; callers must use it once and
     * then call {@link #discard(WarmContainer)}.
     */
    public WarmContainer borrow() throws InterruptedException {
        return borrow(properties.getPool().getBorrowTimeoutMs());
    }

    public WarmContainer borrow(long timeoutMs) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMs));
        while (running.get()) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                refillAsync();
                return null;
            }
            WarmContainer candidate = available.poll(remainingNanos, TimeUnit.NANOSECONDS);
            if (candidate == null) {
                refillAsync();
                return null;
            }
            if (isRunning(candidate)) {
                // Keep the pool warm while this container is executing.
                refillAsync();
                return candidate;
            }
            log.warn("Discarding dead warm container {}", candidate.name());
            all.remove(candidate);
            pendingRemoval.add(candidate);
            removeContainer(candidate);
        }
        return null;
    }

    /**
     * Destroys a used container and replenishes the pool asynchronously.
     */
    public void discard(WarmContainer container) {
        if (container == null) {
            return;
        }
        all.remove(container);
        pendingRemoval.add(container);
        cleanupExecutor.submit(() -> {
            removeContainer(container);
            refillAsync();
        });
    }

    public PoolStatus status() {
        return new PoolStatus(
                properties.getPoolSize(),
                available.size(),
                all.size(),
                pendingRemoval.size(),
                properties.isDockerEnabled() && dockerEngine.isConnected(),
                dockerEngine.getEndpointDescription(),
                properties.getImage(),
                imageReady);
    }

    private void warmupBlocking() {
        int target = properties.getPoolSize();
        if (target <= 0) {
            return;
        }
        long deadline = System.currentTimeMillis() + properties.getPool().getStartupTimeoutMs();
        String lastError = "no container was created";
        while (running.get() && available.size() < target) {
            if (System.currentTimeMillis() > deadline) {
                throw new DockerUnavailableException(
                        "Docker Engine is reachable at " + dockerEngine.getEndpointDescription()
                                + " but the warm pool could not be prepared within "
                                + properties.getPool().getStartupTimeoutMs() + "ms: " + lastError
                                + ". Check JUDGE_IMAGE, registry access and daemon resources.");
            }
            try {
                WarmContainer container = createWarmContainer();
                all.add(container);
                available.add(container);
                refillFailures.set(0);
                log.info("Warm container ready: {} (available {}/{})",
                        container.name(), available.size(), target);
            } catch (Exception e) {
                lastError = ContainerExec.rootMessage(e);
                log.warn("Warm container creation failed: {}. Retrying...", lastError);
                sleepQuietly(250);
            }
        }
    }

    private void startWatchdog() {
        if (watchdog != null || !running.get()) {
            return;
        }
        synchronized (this) {
            if (watchdog != null || !running.get()) {
                return;
            }
            ScheduledExecutorService scheduler =
                    Executors.newSingleThreadScheduledExecutor(daemonFactory("sandbox-pool-watchdog"));
            long interval = Math.max(1_000, properties.getPool().getReconnectIntervalMs());
            scheduler.scheduleWithFixedDelay(this::maintenance, interval, interval, TimeUnit.MILLISECONDS);
            watchdog = scheduler;
        }
    }

    private void maintenance() {
        if (!running.get()) {
            return;
        }
        try {
            if (!dockerEngine.isConnected()) {
                try {
                    dockerEngine.getClient();
                    log.info("Docker Engine reconnected at {}", dockerEngine.getEndpointDescription());
                } catch (RuntimeException e) {
                    log.warn("Docker Engine is still unavailable: {}", e.getMessage());
                    return;
                }
            }
            if (!imageReady) {
                ensureImage();
            }
            pruneDeadContainers();
            retryPendingRemovals();
            if (available.size() < properties.getPoolSize()) {
                refillAsync();
            }
        } catch (RuntimeException e) {
            log.warn("Container pool maintenance failed: {}", ContainerExec.rootMessage(e));
        }
    }

    private void pruneDeadContainers() {
        for (WarmContainer container : available) {
            if (!isRunning(container)) {
                if (available.remove(container)) {
                    log.warn("Removing unhealthy warm container {}", container.name());
                }
                all.remove(container);
                pendingRemoval.add(container);
                removeContainer(container);
            }
        }
    }

    private void retryPendingRemovals() {
        for (WarmContainer container : new HashSet<>(pendingRemoval)) {
            removeContainer(container);
        }
    }

    private void refillAsync() {
        if (!running.get() || properties.getPoolSize() <= available.size()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now < nextRefillAt.get()) {
            return;
        }
        if (!refilling.compareAndSet(false, true)) {
            return;
        }
        refillExecutor.submit(() -> {
            try {
                maintainCapacity();
            } finally {
                refilling.set(false);
            }
        });
    }

    private void maintainCapacity() {
        int target = properties.getPoolSize();
        while (running.get() && available.size() < target) {
            try {
                WarmContainer container = createWarmContainer();
                all.add(container);
                available.add(container);
                refillFailures.set(0);
                nextRefillAt.set(0);
                log.info("Warm container ready: {} (available {}/{})",
                        container.name(), available.size(), target);
            } catch (Exception e) {
                int failures = refillFailures.incrementAndGet();
                long backoff = retryBackoffMs(failures);
                nextRefillAt.set(System.currentTimeMillis() + backoff);
                log.error("Could not create a warm container (failure {}): {}. "
                                + "Next attempt in {}ms.", failures, ContainerExec.rootMessage(e), backoff);
                return;
            }
        }
    }

    private long retryBackoffMs(int failures) {
        long base = Math.max(250, properties.getPool().getRefillRetryBaseMs());
        long max = Math.max(base, properties.getPool().getRefillRetryMaxMs());
        int shift = Math.min(10, Math.max(0, failures - 1));
        return Math.min(max, base * (1L << shift));
    }

    private WarmContainer createWarmContainer() {
        DockerClient client = dockerEngine.getClient();
        JudgeProperties.Execution execution = properties.getExecution();
        int memoryMb = execution.getMaxMemoryLimitMb() + execution.getMemoryOverheadMb();
        long memoryBytes = memoryMb * BYTES_PER_MB;
        long cpuQuota = Math.max(1_000L, Math.round(CPU_PERIOD * execution.getCpuLimit()));
        long now = System.currentTimeMillis();
        String name = properties.getPool().getNamePrefix() + "-" + Long.toHexString(now)
                + "-" + ThreadLocalRandom.current().nextInt(0x10000);

        Map<String, String> labels = Map.of(
                LABEL_MANAGED, "true",
                LABEL_OWNER, ownerId,
                LABEL_CREATED, Long.toString(now));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withNetworkMode("none")
                .withIpcMode("private")
                .withMemory(memoryBytes)
                .withMemorySwap(memoryBytes)
                .withCpuPeriod(CPU_PERIOD)
                .withCpuQuota(cpuQuota)
                .withPidsLimit(execution.getPidsLimit())
                .withReadonlyRootfs(true)
                .withTmpFs(Map.of(
                        properties.getPool().getWorkDir(), tmpfsOptions(execution.getWorkDirSizeMb()),
                        "/tmp", tmpfsOptions(execution.getTmpDirSizeMb())))
                .withShmSize(execution.getShmSizeMb() * BYTES_PER_MB)
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges"))
                .withInit(true)
                .withOomKillDisable(false)
                .withAutoRemove(false);

        String containerId = null;
        try {
            CreateContainerResponse created = client.createContainerCmd(properties.getImage())
                    .withName(name)
                    .withHostConfig(hostConfig)
                    .withUser(properties.getPool().getContainerUser())
                    .withWorkingDir(properties.getPool().getWorkDir())
                    .withEnv("HOME=/tmp", "TMPDIR=/tmp", "LANG=C.UTF-8", "LC_ALL=C.UTF-8")
                    .withCmd("tail", "-f", "/dev/null")
                    .withLabels(labels)
                    .withTty(false)
                    .exec();
            containerId = created.getId();
            client.startContainerCmd(containerId).exec();

            InspectContainerResponse inspect = client.inspectContainerCmd(containerId).exec();
            if (!Boolean.TRUE.equals(inspect.getState().getRunning())) {
                throw new IllegalStateException("container did not enter the running state: "
                        + inspect.getState().getError());
            }

            ContainerExec.Result health = ContainerExec.run(
                    client,
                    containerId,
                    List.of("sh", "-c", "java -version >/dev/null 2>&1 && javac -version >/dev/null 2>&1"),
                    properties.getPool().getWorkDir(),
                    properties.getPool().getHealthCheckTimeoutMs(),
                    4_096);
            if (!health.succeeded()) {
                throw new IllegalStateException("executor health check failed: "
                        + (health.error() != null ? health.error()
                        : health.stderr().isBlank() ? health.stdout() : health.stderr()));
            }

            log.debug("Created isolated warm container {} ({})", name, containerId);
            return new WarmContainer(containerId, name, now);
        } catch (RuntimeException e) {
            if (containerId != null) {
                removeContainerById(containerId);
            }
            throw e;
        }
    }

    private void ensureImage() {
        synchronized (imageLock) {
            if (imageReady) {
                return;
            }
            DockerClient client = dockerEngine.getClient();
            String policy = properties.normalizedImagePullPolicy();
            boolean exists = imageExists(client);

            switch (policy) {
                case "always" -> pullImage(client);
                case "if-missing" -> {
                    if (exists) {
                        log.info("Executor image {} already present.", properties.getImage());
                    } else {
                        pullImage(client);
                    }
                }
                case "never" -> {
                    if (!exists) {
                        throw new DockerUnavailableException(
                                "Executor image " + properties.getImage()
                                        + " is not present locally and JUDGE_IMAGE_PULL_POLICY=never.");
                    }
                    log.info("Executor image {} already present (pull disabled).", properties.getImage());
                }
                default -> throw new DockerUnavailableException(
                        "Unknown JUDGE_IMAGE_PULL_POLICY=" + policy);
            }

            if (!imageExists(client)) {
                throw new DockerUnavailableException(
                        "Executor image " + properties.getImage() + " is still missing after the pull.");
            }
            imageReady = true;
        }
    }

    private boolean imageExists(DockerClient client) {
        try {
            client.inspectImageCmd(properties.getImage()).exec();
            return true;
        } catch (NotFoundException e) {
            return false;
        } catch (DockerClientException e) {
            String message = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
            if (message.contains("404") || message.contains("not found") || message.contains("no such image")) {
                return false;
            }
            throw e;
        }
    }

    private void pullImage(DockerClient client) {
        log.info("Pulling executor image {} (timeout {}ms)...",
                properties.getImage(), properties.getPool().getPullTimeoutMs());
        PullImageResultCallback callback = new PullImageResultCallback() {
            private final AtomicLong lastLog = new AtomicLong();

            @Override
            public void onNext(PullResponseItem item) {
                String status = item.getStatus();
                long now = System.currentTimeMillis();
                if (status != null && now - lastLog.get() > 2_000) {
                    lastLog.set(now);
                    log.info("Image pull: {}", status);
                }
                super.onNext(item);
            }
        };

        try {
            client.pullImageCmd(properties.getImage()).exec(callback);
            if (!callback.awaitCompletion(properties.getPool().getPullTimeoutMs(), TimeUnit.MILLISECONDS)) {
                throw new DockerUnavailableException(
                        "Timed out after " + properties.getPool().getPullTimeoutMs()
                                + "ms while pulling " + properties.getImage() + ".");
            }
            callback.awaitSuccess();
            log.info("Executor image {} pulled successfully.", properties.getImage());
        } catch (DockerUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new DockerUnavailableException(
                    "Could not pull executor image " + properties.getImage() + ": "
                            + ContainerExec.rootMessage(e)
                            + ". Pre-pull the image on the Docker host or fix registry access.",
                    e);
        } finally {
            try {
                callback.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void cleanupStaleContainers() {
        DockerClient client = dockerEngine.getClient();
        try {
            List<Container> containers = client.listContainersCmd()
                    .withShowAll(true)
                    .withLabelFilter(Map.of(LABEL_MANAGED, "true", LABEL_OWNER, ownerId))
                    .exec();
            if (containers.isEmpty()) {
                return;
            }
            log.info("Removing {} stale executor container(s) from a previous process (owner={}).",
                    containers.size(), ownerId);
            for (Container container : containers) {
                removeContainerById(container.getId());
            }
        } catch (Exception e) {
            log.warn("Could not clean stale executor containers: {}", ContainerExec.rootMessage(e));
        }
    }

    private boolean isRunning(WarmContainer container) {
        try {
            InspectContainerResponse inspect =
                    dockerEngine.getClient().inspectContainerCmd(container.id()).exec();
            return Boolean.TRUE.equals(inspect.getState().getRunning());
        } catch (NotFoundException e) {
            return false;
        } catch (Exception e) {
            log.warn("Could not inspect warm container {}: {}",
                    container.name(), ContainerExec.rootMessage(e));
            return false;
        }
    }

    private void removeContainer(WarmContainer container) {
        if (container == null) {
            return;
        }
        if (removeContainerById(container.id())) {
            pendingRemoval.remove(container);
        }
    }

    private boolean removeContainerById(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return true;
        }
        try {
            dockerEngine.getClient().removeContainerCmd(containerId)
                    .withForce(true)
                    .withRemoveVolumes(true)
                    .exec();
            return true;
        } catch (NotFoundException e) {
            // Already gone.
            return true;
        } catch (Exception e) {
            log.warn("Could not remove container {}: {}", shortId(containerId),
                    ContainerExec.rootMessage(e));
            return false;
        }
    }

    private String tmpfsOptions(int sizeMb) {
        return "rw,noexec,nosuid,nodev,size=" + sizeMb + "m,mode=1777";
    }

    @PreDestroy
    public void shutdown() {
        if (!shutdownStarted.compareAndSet(false, true)) {
            return;
        }
        running.set(false);

        if (watchdog != null) {
            watchdog.shutdownNow();
        }
        refillExecutor.shutdownNow();
        cleanupExecutor.shutdownNow();

        Set<WarmContainer> containers = new HashSet<>();
        containers.addAll(available);
        containers.addAll(all);
        containers.addAll(pendingRemoval);
        available.clear();
        all.clear();
        pendingRemoval.clear();

        if (!containers.isEmpty()) {
            log.info("Removing {} executor container(s) during shutdown...", containers.size());
            ExecutorService shutdownExecutor = Executors.newFixedThreadPool(
                    Math.min(4, Math.max(1, containers.size())),
                    daemonFactory("sandbox-pool-shutdown"));
            List<Future<?>> futures = new ArrayList<>();
            for (WarmContainer container : containers) {
                futures.add(shutdownExecutor.submit(() -> removeContainerById(container.id())));
            }
            long deadline = System.currentTimeMillis() + properties.getPool().getCleanupTimeoutMs();
            for (Future<?> future : futures) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    future.cancel(true);
                    continue;
                }
                try {
                    future.get(remaining, TimeUnit.MILLISECONDS);
                } catch (Exception ignored) {
                    future.cancel(true);
                }
            }
            shutdownExecutor.shutdownNow();
        }

        dockerEngine.close();
        log.info("Container pool shut down.");
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "sandbox";
        }
    }

    private static String shortId(String id) {
        return id.length() <= 12 ? id : id.substring(0, 12);
    }

    private static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
