package com.aegis.code_sandbox.service;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.docker.ContainerExec;
import com.aegis.code_sandbox.docker.DockerEngine;
import com.aegis.code_sandbox.dto.ExecutionRequest;
import com.aegis.code_sandbox.dto.ExecutionResult;
import com.aegis.code_sandbox.judge.JudgeOutput;
import com.aegis.code_sandbox.judge.Language;
import com.aegis.code_sandbox.judge.Verdict;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Judge execution entry point.
 *
 * <p>Requests are routed by {@code JUDGE_EXECUTION_BACKEND}:</p>
 * <ul>
 *   <li><b>onlinecompiler</b> (default) — the generated harness is compiled and
 *       run by the OnlineCompiler.io HTTPS API. No Docker host is involved.</li>
 *   <li><b>docker</b> (secondary) — the harness is compiled and run in a warm,
 *       disposable Docker container from {@link ContainerPool}.</li>
 * </ul>
 *
 * <p>Both backends return the same {@link ExecutionResult} contract and share
 * {@link JudgeOutput} for the harness line protocol, so verdicts do not depend
 * on where the code ran.</p>
 */
@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);

    private static final long BYTES_PER_KB = 1024L;
    private static final long CPU_PERIOD = 100_000L;
    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final long MAX_HARD_WALL_MS = 600_000L;

    private final ContainerPool containerPool;
    private final DockerEngine dockerEngine;
    private final OnlineCompilerExecutionService onlineCompiler;
    private final JudgeProperties properties;

    public CodeExecutionService(
            ContainerPool containerPool,
            DockerEngine dockerEngine,
            OnlineCompilerExecutionService onlineCompiler,
            JudgeProperties properties
    ) {
        this.containerPool = containerPool;
        this.dockerEngine = dockerEngine;
        this.onlineCompiler = onlineCompiler;
        this.properties = properties;
    }

    public ExecutionResult executeCode(ExecutionRequest request) {
        if (!properties.isExecutionEnabled()) {
            return internalError("Code execution is disabled by the sandbox operator.");
        }
        if (request == null || request.code() == null || request.code().isBlank()) {
            return internalError("No source code was provided.");
        }
        Language language = Language.resolve(request.language());

        byte[] source = request.code().getBytes(StandardCharsets.UTF_8);
        if (source.length > properties.effectiveMaxCodeBytes()) {
            return internalError("Submitted code exceeds the " + properties.effectiveMaxCodeBytes()
                    + "-byte limit.");
        }

        Limit limit = resolveLimits(request);
        log.info("Judge request: language={} requested={}ms effective={}ms memory={}MB "
                        + "(requested {}KB, backend={})",
                language.id(), limit.requestedTimeMs(), limit.effectiveTimeMs(), limit.memoryMb(),
                limit.requestedMemoryKb(), properties.normalizedBackend());

        if (properties.isOnlineCompilerBackend()) {
            return onlineCompiler.execute(request, limit.effectiveTimeMs(), limit.memoryMb());
        }

        if (!language.dockerSupported()) {
            return internalError("The " + language.displayName()
                    + " runtime is only available on the OnlineCompiler backend. "
                    + "This sandbox is configured for Docker, which supports Java only.");
        }

        ContainerPool.WarmContainer container = null;
        try {
            container = containerPool.borrow();
            if (container == null) {
                return internalError("No execution container is available. The warm pool is replenishing; retry shortly.");
            }

            DockerClient client = dockerEngine.getClient();
            String containerId = container.id();
            String workDir = properties.getPool().getWorkDir();

            copySource(client, containerId, source, workDir);

            long compileStart = System.nanoTime();
            ContainerExec.Result compile = ContainerExec.run(
                    client,
                    containerId,
                    compileCommand(workDir),
                    workDir,
                    properties.getExecution().getCompileTimeoutMs(),
                    properties.getExecution().getMaxOutputBytes());
            long compileWallMs = elapsedMs(compileStart);

            if (compile.timedOut()) {
                killContainer(client, containerId);
                return compilationError("Compilation timed out after "
                        + properties.getExecution().getCompileTimeoutMs()
                        + "ms. Reduce the size/complexity of the submitted source.", compile, compileWallMs);
            }
            if (compile.error() != null) {
                return internalError("Compiler could not run: " + compile.error());
            }
            if (compile.exitCode() == 137) {
                return compilationError("The compiler was killed by the container (exit 137), usually because "
                        + "the compilation exceeded the container memory limit.", compile, compileWallMs);
            }
            if (compile.exitCode() != 0) {
                return compilationError(compilerOutput(compile), compile, compileWallMs);
            }

            applyRunLimits(client, containerId, limit);

            long cpuBefore = readCpuNanos(client, containerId, workDir);
            boolean cpuAccounting = cpuBefore >= 0;
            long hardWallMs = cpuAccounting
                    ? cpuHardWallMs(limit.effectiveTimeMs())
                    : limit.effectiveTimeMs() + properties.getExecution().getWallClockGraceMs();

            String memoryNonce = Long.toHexString(ThreadLocalRandom.current().nextLong());
            Pattern memoryPattern = memoryMarkerPattern(memoryNonce);

            log.debug("Running container {} with hard wall limit {}ms (CPU accounting={})",
                    container.name(), hardWallMs, cpuAccounting);
            long runStart = System.nanoTime();
            ContainerExec.Result run = ContainerExec.run(
                    client,
                    containerId,
                    javaCommand(limit, workDir, memoryNonce),
                    workDir,
                    hardWallMs,
                    properties.getExecution().getMaxOutputBytes());
            long wallNanos = System.nanoTime() - runStart;
            long wallMs = TimeUnit.NANOSECONDS.toMillis(wallNanos);

            long memoryKb = 0;
            String cleanStderr = run.stderr();
            Matcher memoryMatcher = memoryPattern.matcher(cleanStderr);
            if (memoryMatcher.find()) {
                try {
                    memoryKb = Long.parseLong(memoryMatcher.group(1));
                } catch (NumberFormatException ignored) {
                    // A malformed marker can only come from a corrupted stream.
                }
                cleanStderr = memoryMatcher.replaceAll("").strip();
                run = new ContainerExec.Result(
                        run.exitCode(),
                        run.stdout(),
                        cleanStderr,
                        run.truncated(),
                        run.timedOut(),
                        run.error());
            }

            long cpuNanos = -1;
            if (run.timedOut()) {
                // Capture the CPU consumed so far before the container is
                // destroyed, then enforce the authoritative hard wall.
                long cpuAtTimeout = readCpuNanos(client, containerId, workDir);
                if (cpuAccounting && cpuAtTimeout >= cpuBefore) {
                    cpuNanos = cpuAtTimeout - cpuBefore;
                }
                killContainer(client, containerId);
            } else {
                long cpuAfter = readCpuNanos(client, containerId, workDir);
                if (cpuAccounting && cpuAfter >= cpuBefore) {
                    cpuNanos = cpuAfter - cpuBefore;
                    if (!plausibleCpuTime(cpuNanos, wallNanos)) {
                        log.warn("Ignoring implausible cgroup CPU delta {}ns over {}ms wall; "
                                + "falling back to wall time.", cpuNanos, wallMs);
                        cpuNanos = -1;
                    }
                }
            }

            long cpuMs = cpuNanos >= 0 ? TimeUnit.NANOSECONDS.toMillis(cpuNanos) : -1;
            long reportedMs = cpuMs >= 0 ? cpuMs : wallMs;
            JudgeOutput.PassCounts counts = JudgeOutput.parseCounts(run.stdout());

            if (run.timedOut()) {
                return new ExecutionResult(
                        Verdict.TLE,
                        "Time limit exceeded (limit " + limit.effectiveTimeMs() + "ms; hard wall "
                                + hardWallMs + "ms; used " + reportedMs + "ms CPU / " + wallMs + "ms wall).",
                        reportedMs,
                        wallMs,
                        cpuMs,
                        memoryKb,
                        limit.memoryMb() * BYTES_PER_KB,
                        run.exitCode(),
                        run.stdout(),
                        run.stderr(),
                        run.truncated(),
                        counts.passed(),
                        counts.total());
            }

            long effectiveCpuMs = cpuMs >= 0 ? cpuMs : wallMs;
            boolean cpuOverLimit = cpuMs >= 0
                    && cpuMs > limit.effectiveTimeMs() + properties.getExecution().getTimeLimitToleranceMs();
            boolean wallOverLimit = wallMs >= hardWallMs;
            if (cpuOverLimit || wallOverLimit) {
                return new ExecutionResult(
                        Verdict.TLE,
                        "Time limit exceeded (limit " + limit.effectiveTimeMs() + "ms; used "
                                + effectiveCpuMs + "ms CPU / " + wallMs + "ms wall).",
                        effectiveCpuMs,
                        wallMs,
                        cpuMs,
                        memoryKb,
                        limit.memoryMb() * BYTES_PER_KB,
                        run.exitCode(),
                        run.stdout(),
                        run.stderr(),
                        run.truncated(),
                        counts.passed(),
                        counts.total());
            }

            String combinedOutput = run.stdout() + "\n" + run.stderr();
            if (run.exitCode() == 137 || looksLikeOom(combinedOutput)) {
                return new ExecutionResult(
                        Verdict.MLE,
                        "Memory limit exceeded (limit " + limit.memoryMb() + "MB; observed "
                                + (memoryKb / 1024) + "MB).",
                        reportedMs,
                        wallMs,
                        cpuMs,
                        memoryKb,
                        limit.memoryMb() * BYTES_PER_KB,
                        run.exitCode(),
                        run.stdout(),
                        run.stderr(),
                        run.truncated(),
                        counts.passed(),
                        counts.total());
            }

            if (run.error() != null) {
                return internalError("Execution stream failed before the process exited: " + run.error());
            }
            if (run.exitCode() != 0) {
                return new ExecutionResult(
                        Verdict.RE,
                        "Runtime error (exit code " + run.exitCode() + ").",
                        reportedMs,
                        wallMs,
                        cpuMs,
                        memoryKb,
                        limit.memoryMb() * BYTES_PER_KB,
                        run.exitCode(),
                        run.stdout(),
                        run.stderr(),
                        run.truncated(),
                        counts.passed(),
                        counts.total());
            }

            Verdict verdict = JudgeOutput.classify(run.stdout(), run.stderr(), counts);
            String message = switch (verdict) {
                case AC -> counts.known()
                        ? "All " + counts.total() + " test case(s) passed."
                        : "Execution completed successfully.";
                case WA -> counts.known()
                        ? "Wrong answer: " + counts.passed() + "/" + counts.total() + " test case(s) passed."
                        : "Wrong answer.";
                case RE -> "Runtime error reported by the test harness.";
                default -> "Execution completed.";
            };

            log.info("Judge result: verdict={} container={} time={}ms wall={}ms cpu={}ms memory={}KB exit={}",
                    verdict, container.name(), reportedMs, wallMs, cpuMs, memoryKb, run.exitCode());

            return new ExecutionResult(
                    verdict,
                    message,
                    reportedMs,
                    wallMs,
                    cpuMs,
                    memoryKb,
                    limit.memoryMb() * BYTES_PER_KB,
                    run.exitCode(),
                    run.stdout(),
                    run.stderr(),
                    run.truncated(),
                    counts.passed(),
                    counts.total());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return internalError("Execution was interrupted while waiting for a container.");
        } catch (Exception e) {
            log.error("Judge execution failed", e);
            return internalError("Execution failed: " + ContainerExec.rootMessage(e));
        } finally {
            if (container != null) {
                containerPool.discard(container);
            }
        }
    }

    /**
     * Writes the submitted source into the container's tmpfs.
     *
     * <p>The Docker archive/copy API refuses to write into a container whose
     * root filesystem is read-only, even when the destination is a writable
     * tmpfs, so the source is streamed over {@code docker exec} stdin instead.
     * The reader is told the exact byte count with {@code head -c} because the
     * Engine API does not signal stdin EOF on hijacked connections; the
     * following {@code wc -c} verifies that no bytes were lost.</p>
     */
    private void copySource(DockerClient client, String containerId, byte[] source, String workDir) {
        String path = workDir + "/Main.java";
        ContainerExec.Result write = ContainerExec.run(
                client,
                containerId,
                List.of("sh", "-c", "head -c " + source.length + " > " + path),
                workDir,
                properties.getExecution().getSourceWriteTimeoutMs(),
                4_096,
                source);
        if (write.timedOut()) {
            throw new IllegalStateException("Timed out after "
                    + properties.getExecution().getSourceWriteTimeoutMs()
                    + "ms while transferring the submitted source.");
        }
        // Named pipes can report "The pipe has been ended" after the hijacked
        // stdin connection is closed by the daemon even though the writer
        // exited normally. Never trust that stream status: read the byte count
        // back with a separate, non-hijacked exec and compare it.
        ContainerExec.Result verify = ContainerExec.run(
                client,
                containerId,
                List.of("sh", "-c", "wc -c < " + path),
                workDir,
                2_000,
                4_096);
        if (verify.timedOut()) {
            throw new IllegalStateException("Timed out while verifying the transferred source.");
        }
        if (verify.exitCode() != 0) {
            String detail = (verify.stderr().isBlank() ? verify.stdout() : verify.stderr()).strip();
            throw new IllegalStateException("Could not verify the transferred source (exit "
                    + verify.exitCode() + "): " + detail);
        }
        long written;
        try {
            written = Long.parseLong(verify.stdout().strip());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("Source transfer verification failed: unexpected "
                    + "'wc -c' output '" + verify.stdout().strip() + "'.");
        }
        if (written != source.length) {
            throw new IllegalStateException("Source transfer was incomplete: expected "
                    + source.length + " bytes but the container wrote " + written
                    + (write.error() == null ? "." : " (" + write.error() + ")."));
        }
        if (write.error() != null) {
            log.debug("Source transfer finished with a stream close notice: {}", write.error());
        }
    }

    private List<String> compileCommand(String workDir) {
        int heapMb = Math.max(64, Math.min(512, properties.getExecution().getCompileMemoryMb() / 2));
        return List.of(
                "javac",
                "-encoding", "UTF-8",
                "-proc:none",
                "-J-Xmx" + heapMb + "m",
                "-d", workDir,
                workDir + "/Main.java");
    }

    /**
     * Runs the submitted program under a tiny shell supervisor. The supervisor
     * records the JVM's high-water RSS from {@code /proc/<pid>/status} and
     * prints it on stderr with a per-request nonce. This keeps measurement
     * inside the container without polling the Docker API (whose one-second
     * cadence misses short runs) and without charging an external sampler's
     * CPU time to the contestant.
     */
    private static final String RUN_WRAPPER = """
            "$@" &
            pid=$!
            max=0
            while :; do
              state=""
              while read -r key value rest; do
                case "$key" in
                  VmHWM:) if [ -n "$value" ] && [ "$value" -gt "$max" ]; then max=$value; fi ;;
                  State:) state=$value ;;
                esac
              done 2>/dev/null < "/proc/$pid/status"
              if [ -z "$state" ] || [ "$state" = "Z" ]; then break; fi
              sleep 0.05
            done
            wait "$pid"; rc=$?
            echo "JUDGE_MEM_KB_%s=$max" >&2
            exit $rc
            """;

    private List<String> javaCommand(Limit limit, String workDir, String memoryNonce) {
        JudgeProperties.Execution execution = properties.getExecution();
        List<String> command = new ArrayList<>();
        command.add("sh");
        command.add("-c");
        command.add(RUN_WRAPPER.formatted(memoryNonce));
        command.add("judge-runner");
        command.add("java");
        command.add("-Xms" + Math.max(4, Math.min(16, limit.memoryMb() / 4)) + "m");
        command.add("-Xmx" + limit.memoryMb() + "m");
        command.add("-XX:ActiveProcessorCount=" + execution.getActiveProcessorCount());
        command.add("-XX:+ExitOnOutOfMemoryError");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Duser.home=/tmp");
        command.add("-Djava.io.tmpdir=/tmp");
        if (execution.getExtraJvmArgs() != null && !execution.getExtraJvmArgs().isBlank()) {
            Arrays.stream(execution.getExtraJvmArgs().trim().split("\\s+"))
                    .filter(arg -> !arg.isBlank())
                    .forEach(command::add);
        }
        command.add("-cp");
        command.add(workDir);
        command.add("Main");
        return command;
    }

    private static Pattern memoryMarkerPattern(String nonce) {
        return Pattern.compile("(?m)^\\s*JUDGE_MEM_KB_" + Pattern.quote(nonce) + "=(\\d+)\\s*$");
    }

    private void applyRunLimits(DockerClient client, String containerId, Limit limit) {
        JudgeProperties.Execution execution = properties.getExecution();
        long memoryBytes = (limit.memoryMb() + execution.getMemoryOverheadMb()) * BYTES_PER_MB;
        long cpuQuota = Math.max(1_000L, Math.round(CPU_PERIOD * execution.getCpuLimit()));
        try {
            client.updateContainerCmd(containerId)
                    .withMemory(memoryBytes)
                    .withMemorySwap(memoryBytes)
                    .withCpuPeriod(CPU_PERIOD)
                    .withCpuQuota(cpuQuota)
                    .withPidsLimit(execution.getPidsLimit())
                    .exec();
        } catch (Exception e) {
            // The warm container was created with the maximum allowed limits.
            // The JVM -Xmx still enforces the requested heap limit if the
            // daemon refuses a cgroup update (for example on older kernels).
            log.warn("Could not apply per-request cgroup limits to {}: {}. "
                            + "Relying on the JVM heap limit and the warm container hard limits.",
                    shortId(containerId), ContainerExec.rootMessage(e));
        }
    }

    /**
     * CPU time is the primary measurement when cgroups expose it. The hard wall
     * is deliberately generous enough for the configured CPU quota: a 0.5 CPU
     * container needs roughly twice the wall time to consume a CPU-second.
     */
    private long cpuHardWallMs(int effectiveTimeLimitMs) {
        double cpu = Math.max(0.1, properties.getExecution().getCpuLimit());
        long wall = (long) Math.ceil(effectiveTimeLimitMs / cpu);
        return Math.min(MAX_HARD_WALL_MS, wall + properties.getExecution().getWallClockGraceMs());
    }

    private boolean plausibleCpuTime(long cpuNanos, long wallNanos) {
        if (cpuNanos <= 0 || wallNanos <= 0) {
            return false;
        }
        double maximumPlausible = (wallNanos * Math.max(1.0, properties.getExecution().getCpuLimit()) * 1.5)
                + TimeUnit.MILLISECONDS.toNanos(500);
        return cpuNanos <= maximumPlausible;
    }

    /**
     * Reads the container's cumulative CPU counter. cgroup v2 is preferred; the
     * cgroup v1 cpuacct file is the fallback. Returns -1 when unavailable.
     */
    private long readCpuNanos(DockerClient client, String containerId, String workDir) {
        ContainerExec.Result result = ContainerExec.run(
                client,
                containerId,
                List.of("sh", "-c",
                        "cat /sys/fs/cgroup/cpu.stat 2>/dev/null "
                                + "|| cat /sys/fs/cgroup/cpuacct/cpuacct.usage 2>/dev/null "
                                + "|| cat /sys/fs/cgroup/cpu,cpuacct/cpuacct.usage 2>/dev/null "
                                + "|| cat /sys/fs/cgroup/cpu/cpuacct.usage 2>/dev/null "
                                + "|| echo UNKNOWN"),
                workDir,
                2_000,
                8_192);
        if (!result.succeeded()) {
            return -1;
        }
        String output = result.stdout().trim();
        if (output.isEmpty() || output.contains("UNKNOWN")) {
            return -1;
        }
        try {
            for (String line : output.split("\\R")) {
                String[] parts = line.trim().split("\\s+");
                if (parts.length == 2 && parts[0].equals("usage_usec")) {
                    return Math.multiplyExact(Long.parseLong(parts[1]), 1_000L);
                }
            }
            if (output.matches("\\d+")) {
                return Long.parseLong(output);
            }
        } catch (ArithmeticException | NumberFormatException e) {
            log.debug("Could not parse cgroup CPU counter '{}': {}", output, e.getMessage());
        }
        return -1;
    }

    Limit resolveLimits(ExecutionRequest request) {
        JudgeProperties.Execution execution = properties.getExecution();

        int requestedTime = request.timeLimitMs() == null
                ? execution.getDefaultTimeLimitMs()
                : request.timeLimitMs();
        if (requestedTime <= 0) {
            requestedTime = execution.getDefaultTimeLimitMs();
        }
        if (requestedTime > execution.getMaxTimeLimitMs()) {
            log.warn("Requested time limit {}ms exceeds MAX_TIME_LIMIT_MS={}; clamping.",
                    requestedTime, execution.getMaxTimeLimitMs());
            requestedTime = execution.getMaxTimeLimitMs();
        }
        long effectiveTime = Math.round(requestedTime * execution.getTimeLimitMultiplier());
        effectiveTime = Math.max(1, Math.min(execution.getMaxTimeLimitMs(), effectiveTime));

        int requestedMemoryKb = request.memoryLimitKb() == null
                ? execution.getDefaultMemoryLimitMb() * 1024
                : request.memoryLimitKb();
        if (requestedMemoryKb <= 0) {
            requestedMemoryKb = execution.getDefaultMemoryLimitMb() * 1024;
        }
        int memoryMb = (int) Math.ceil(requestedMemoryKb / (double) 1024);
        if (memoryMb < execution.getMinMemoryLimitMb()) {
            memoryMb = execution.getMinMemoryLimitMb();
        }
        if (memoryMb > execution.getMaxMemoryLimitMb()) {
            log.warn("Requested memory limit {}MB exceeds MAX_MEMORY_LIMIT_MB={}; clamping.",
                    memoryMb, execution.getMaxMemoryLimitMb());
            memoryMb = execution.getMaxMemoryLimitMb();
        }

        return new Limit(requestedTime, (int) effectiveTime, memoryMb, requestedMemoryKb);
    }

    private ExecutionResult compilationError(String output, ContainerExec.Result compile, long compileWallMs) {
        String text = output == null || output.isBlank() ? "Compilation failed." : output;
        return new ExecutionResult(
                Verdict.CE,
                text,
                0,
                compileWallMs,
                -1,
                0,
                0,
                compile.exitCode(),
                compile.stdout(),
                compile.stderr(),
                compile.truncated(),
                0,
                null);
    }

    private String compilerOutput(ContainerExec.Result compile) {
        String output = compile.stderr() == null ? "" : compile.stderr();
        if (output.isBlank()) {
            output = compile.stdout() == null ? "" : compile.stdout();
        }
        if (output.isBlank()) {
            output = "Compilation failed with exit code " + compile.exitCode() + ".";
        }
        if (compile.truncated()) {
            output = output + "\n[compiler output truncated by the sandbox]";
        }
        return output.strip();
    }

    private ExecutionResult internalError(String message) {
        return new ExecutionResult(
                Verdict.IE,
                message,
                0,
                0,
                -1,
                0,
                0,
                -1,
                "",
                "",
                false,
                null,
                null);
    }

    private void killContainer(DockerClient client, String containerId) {
        try {
            client.killContainerCmd(containerId).exec();
        } catch (NotFoundException ignored) {
            // Already gone.
        } catch (Exception e) {
            log.debug("Could not kill container {}: {}", shortId(containerId),
                    ContainerExec.rootMessage(e));
        }
    }

    private boolean looksLikeOom(String output) {
        if (output == null || output.isBlank()) {
            return false;
        }
        String normalized = output.toLowerCase(Locale.ROOT);
        return normalized.contains("outofmemoryerror")
                || normalized.contains("java heap space")
                || normalized.contains("gc overhead limit exceeded")
                || normalized.contains("could not reserve enough space for object heap")
                || normalized.contains("native memory allocation")
                || (normalized.contains("killed") && normalized.contains("memory"));
    }

    private static long elapsedMs(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }

    private static String shortId(String id) {
        return id == null ? "?" : id.length() <= 12 ? id : id.substring(0, 12);
    }

    record Limit(int requestedTimeMs, int effectiveTimeMs, int memoryMb, int requestedMemoryKb) {
    }

}
