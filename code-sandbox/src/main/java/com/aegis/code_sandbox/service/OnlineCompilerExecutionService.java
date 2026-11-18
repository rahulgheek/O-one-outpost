package com.aegis.code_sandbox.service;

import com.aegis.code_sandbox.config.JudgeProperties;
import com.aegis.code_sandbox.dto.ExecutionRequest;
import com.aegis.code_sandbox.dto.ExecutionResult;
import com.aegis.code_sandbox.judge.JudgeOutput;
import com.aegis.code_sandbox.judge.Language;
import com.aegis.code_sandbox.judge.Verdict;
import com.aegis.code_sandbox.online.OnlineCompilerClient;
import com.aegis.code_sandbox.online.OnlineCompilerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Executes submissions through the OnlineCompiler.io synchronous API.
 *
 * <p>The provider compiles and runs the complete generated harness in a remote
 * sandbox. Its run time and peak memory are reported back; CPU time is not
 * exposed by the API, so {@code cpuTimeMs} is {@code -1}. Provider exit code
 * 124 is TLE and 137 is MLE. A provider timeout is never retried because the
 * remote run may already have been billed.</p>
 *
 * <p>The requested language selects both the provider compiler id and the
 * compiler/memory diagnostic patterns used for verdict classification.</p>
 */
@Service
public class OnlineCompilerExecutionService {

    private static final Logger log = LoggerFactory.getLogger(OnlineCompilerExecutionService.class);
    private static final long BYTES_PER_KB = 1024L;

    private final OnlineCompilerClient client;
    private final JudgeProperties properties;

    public OnlineCompilerExecutionService(OnlineCompilerClient client, JudgeProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    public ExecutionResult execute(ExecutionRequest request, int effectiveTimeMs, int memoryMb) {
        JudgeProperties.OnlineCompiler config = properties.getOnlineCompiler();
        Language language = Language.resolve(request.language());
        String compiler = config.compilerFor(language);

        byte[] source = request.code().getBytes(StandardCharsets.UTF_8);
        if (source.length > config.getMaxCodeBytes()) {
            return compilationError("Submitted code is " + source.length
                    + " bytes; OnlineCompiler accepts at most " + config.getMaxCodeBytes() + " bytes.");
        }
        String input = request.input() == null ? "" : request.input();
        long inputBytes = input.getBytes(StandardCharsets.UTF_8).length;
        if (inputBytes > config.getMaxInputBytes()) {
            return internalError("Standard input is " + inputBytes
                    + " bytes; OnlineCompiler accepts at most " + config.getMaxInputBytes() + " bytes.");
        }

        int providerMemoryMb = providerMemoryMb(memoryMb);
        long started = System.nanoTime();
        OnlineCompilerClient.RunResponse response;
        try {
            response = client.run(request.code(), input, effectiveTimeMs, providerMemoryMb, compiler);
        } catch (OnlineCompilerException e) {
            return providerFailure(e);
        }
        long localWallMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        String stdout = response.output() == null ? "" : response.output();
        String stderr = response.error() == null ? "" : response.error();
        String combined = stdout + "\n" + stderr;
        long reportedMs = secondsToMillis(response.time(), effectiveTimeMs);
        long totalMs = secondsToMillis(response.total(), localWallMs);
        long memoryKb = parseLong(response.memory(), 0);
        int exitCode = response.exitCode();
        JudgeOutput.PassCounts counts = JudgeOutput.parseCounts(stdout);

        Verdict verdict;
        String message;

        if (exitCode == 124) {
            verdict = Verdict.TLE;
            message = "Time limit exceeded (limit " + effectiveTimeMs + "ms; provider stopped after "
                    + reportedMs + "ms).";
        } else if (exitCode == 137 || (exitCode != 0 && mentionsMemoryLimit(language, combined))) {
            verdict = Verdict.MLE;
            message = "Memory limit exceeded (requested " + memoryMb + "MB; observed "
                    + (memoryKb / BYTES_PER_KB) + "MB).";
        } else if (exitCode != 0) {
            if (looksLikeCompilationError(language, combined)) {
                verdict = Verdict.CE;
                message = "Compilation failed.";
            } else if (exitCode == 153) {
                verdict = Verdict.RE;
                message = "Runtime error: the program produced more than the provider's output flood limit.";
            } else if (exitCode == 71) {
                verdict = Verdict.RE;
                message = "Runtime error: the program exceeded the provider's process limit.";
            } else {
                verdict = Verdict.RE;
                message = "Runtime error (exit code " + exitCode + ").";
            }
        } else if ("error".equalsIgnoreCase(response.status())) {
            if (looksLikeCompilationError(language, combined)) {
                verdict = Verdict.CE;
                message = "Compilation failed.";
            } else {
                verdict = Verdict.RE;
                message = "Runtime error reported by OnlineCompiler.";
            }
        } else {
            verdict = JudgeOutput.classify(stdout, stderr, counts);
            message = switch (verdict) {
                case AC -> counts.known()
                        ? "All " + counts.total() + " test case(s) passed."
                        : "Execution completed successfully.";
                case WA -> counts.known()
                        ? "Wrong answer: " + counts.passed() + "/" + counts.total() + " test case(s) passed."
                        : "Wrong answer.";
                case RE -> "Runtime error reported by the test harness.";
                default -> "Execution completed.";
            };
        }

        long timeMs = verdict == Verdict.CE ? 0 : reportedMs;
        long wallMs = totalMs > 0 ? totalMs : localWallMs;
        int passed = counts.passed() == null ? 0 : counts.passed();

        log.info("OnlineCompiler result: language={} compiler={} verdict={} time={}ms total={}ms "
                        + "memory={}KB exit={} status={}",
                language.id(), compiler, verdict, timeMs, wallMs, memoryKb, exitCode, response.status());

        return new ExecutionResult(
                verdict,
                message,
                timeMs,
                wallMs,
                -1,
                memoryKb,
                memoryMb * BYTES_PER_KB,
                exitCode,
                stdout,
                stderr,
                response.truncated(),
                counts.known() ? passed : null,
                counts.known() ? counts.total() : null);
    }

    private int providerMemoryMb(int requestedMb) {
        JudgeProperties.OnlineCompiler config = properties.getOnlineCompiler();
        int withHeadroom = Math.max(16, requestedMb) + Math.max(0, config.getMemoryHeadroomMb());
        return Math.max(16, Math.min(config.getMaxMemoryMb(), withHeadroom));
    }

    /**
     * Combines the per-language diagnostics with the provider's generic
     * messages. Older provider responses report a bare "compilation failed" or
     * "compiler error" without naming the language, and dropping that fallback
     * would turn a genuine CE into an RE.
     */
    private static boolean looksLikeCompilationError(Language language, String combinedOutput) {
        if (language.looksLikeCompilationError(combinedOutput)) {
            return true;
        }
        if (combinedOutput == null || combinedOutput.isBlank()) {
            return false;
        }
        String lower = combinedOutput.toLowerCase(Locale.ROOT);
        return lower.contains("compilation failed")
                || lower.contains("compiler error")
                || lower.contains("compilation error");
    }

    /** Per-language memory hints plus the provider's generic kill message. */
    private static boolean mentionsMemoryLimit(Language language, String combinedOutput) {
        if (language.mentionsMemoryLimit(combinedOutput)) {
            return true;
        }
        return combinedOutput != null
                && combinedOutput.toLowerCase(Locale.ROOT).contains("memory limit exceeded");
    }

    private ExecutionResult providerFailure(OnlineCompilerException e) {
        String message;
        if (e.statusCode() == 401 || e.statusCode() == 403) {
            message = "OnlineCompiler authentication failed. Check ONLINECOMPILER_API_KEY. " + e.getMessage();
        } else if (e.statusCode() == 429) {
            message = "OnlineCompiler is at its rate or capacity limit. Retry shortly. " + e.getMessage();
        } else {
            message = e.getMessage();
        }
        log.warn("OnlineCompiler execution failed (status={}): {}", e.statusCode(), message);
        return internalError(message);
    }

    private static long secondsToMillis(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            BigDecimal seconds = new BigDecimal(value.trim());
            BigDecimal millis = seconds.movePointRight(3).setScale(0, RoundingMode.HALF_UP);
            return Math.max(0, millis.longValue());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static long parseLong(String value, long fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            try {
                return new BigDecimal(value.trim()).setScale(0, RoundingMode.HALF_UP).longValue();
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
    }

    private ExecutionResult compilationError(String detail) {
        return new ExecutionResult(
                Verdict.CE,
                detail,
                0,
                0,
                -1,
                0,
                0,
                -1,
                "",
                detail,
                false,
                0,
                null);
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
}
