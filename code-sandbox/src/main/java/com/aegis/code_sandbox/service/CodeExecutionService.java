package com.aegis.code_sandbox.service;

import com.aegis.code_sandbox.dto.ExecutionRequest;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);
    private final ExecutorService cleanupExecutor = Executors.newCachedThreadPool();

    private final ContainerPool containerPool;

    public CodeExecutionService(ContainerPool containerPool) {
        this.containerPool = containerPool;
    }

    public String executeCode(ExecutionRequest request) {
        String containerName = null;
        ExecutorService streamReaders = Executors.newFixedThreadPool(2);

        log.info("TIME LIMIT FROM REQUEST:   {}ms", request.timeLimitMs());
        log.info("MEMORY LIMIT FROM REQUEST: {}kb", request.memoryLimitKb());

        try {
            // ── 1. Borrow a pre-warmed container ──────────────────────────────────
            long t0 = System.currentTimeMillis();
            containerName = containerPool.borrow();
            log.info("BORROW took: {}ms", (System.currentTimeMillis() - t0));

            if (containerName == null) {
                return "SYSTEM_ERROR|No containers available, please try again.";
            }

            long memoryMb = request.memoryLimitKb() / 1024;

            // ── FIX 2: Write code via stdin — eliminates docker cp (saves ~150ms) ─
            //    -i flag keeps stdin open so we can pipe the source file in directly
            String[] execCmd = {
                    "docker", "exec", "-i",
                    containerName,
                    "sh", "-c",
                    "cat > /app/Main.java && javac -J-Xmx128m /app/Main.java && java -Xmx" + memoryMb + "m -cp /app Main"
            };

            ProcessBuilder pb = new ProcessBuilder(execCmd);
            pb.redirectErrorStream(false); // keep stdout and stderr separate
            Process process = pb.start();

            // Write source code to container stdin — no temp file, no docker cp
            long t1 = System.currentTimeMillis();
            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(request.code().getBytes());
                stdin.flush();
            } // closing stdin signals EOF so 'cat' finishes writing Main.java
            log.info("STDIN WRITE took: {}ms", (System.currentTimeMillis() - t1));

            // ── FIX 1: Read stdout AND stderr concurrently on background threads ──
            //    Without this, if output fills the OS pipe buffer (~64KB) the
            //    process blocks trying to write, but we're blocked in waitFor()
            //    → classic deadlock that looks like TLE
            Future<String> stdoutFuture = streamReaders.submit(
                    () -> readStream(process.getInputStream())   // stdout
            );
            Future<String> stderrFuture = streamReaders.submit(
                    () -> readStream(process.getErrorStream())   // stderr
            );

            // ── 2. Wait exactly timeLimitMs — no padding since no cold start ──────
            long t2 = System.currentTimeMillis();
            boolean finished = process.waitFor(request.timeLimitMs(), TimeUnit.MILLISECONDS);
            log.info("EXEC took:  {}ms", (System.currentTimeMillis() - t2));
            log.info("TOTAL took: {}ms", (System.currentTimeMillis() - t0));

            if (!finished) {
                // Cancel stream readers before killing process
                stdoutFuture.cancel(true);
                stderrFuture.cancel(true);
                process.destroyForcibly();
                return "TIME_LIMIT_EXCEEDED";
            }

            // ── 3. Collect output — guarded by timeout so we never hang here ──────
            String output;
            String errorOutput;
            try {
                output      = stdoutFuture.get(3, TimeUnit.SECONDS);
                errorOutput = stderrFuture.get(3, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                return "TIME_LIMIT_EXCEEDED";
            }

            // ── 4. Classify the result ────────────────────────────────────────────
            int exitCode = process.exitValue();

            if (exitCode == 137) {
                // Linux OOM killer sends SIGKILL (128 + 9 = 137)
                return "MEMORY_LIMIT_EXCEEDED";
            }

            if (!errorOutput.isEmpty()) {
                // javac errors always contain "error:" — runtime exceptions don't
                if (errorOutput.contains("error:")) {
                    return "COMPILATION_ERROR\n" + errorOutput;
                }
                return "RUNTIME_ERROR\n" + errorOutput;
            }

            return output.isEmpty() ? "(no output)" : output;

        } catch (Exception e) {
            log.error("Execution error", e);
            return "SYSTEM_ERROR|" + e.getMessage();
        } finally {
            streamReaders.shutdownNow();

            // Always kill the used container — never reuse it (class files could leak)
            // Spin up a fresh replacement in the background so the pool stays full
            if (containerName != null) {
                final String used = containerName;
                cleanupExecutor.submit(() -> {
                    try {
                        new ProcessBuilder("docker", "rm", "-f", used)
                                .start()
                                .waitFor(3, TimeUnit.SECONDS);
                        log.info("Used container removed: {}", used);
                    } catch (Exception ignored) {}
                });

                containerPool.returnFresh(); // async — doesn't block this request
            }
        }
    }

    private String readStream(InputStream is) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        return sb.toString().trim();
    }
}