package com.aegis.code_sandbox.docker;

import com.aegis.code_sandbox.util.BoundedOutputStream;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.ExecStartCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a command inside an already-running executor container and captures a
 * bounded amount of stdout/stderr.
 *
 * <p>The caller owns the container lifecycle. If {@link Result#timedOut()} is
 * true the caller must destroy the container: the command may still be running
 * and there is no reliable way to signal a single exec process after the
 * Docker API stream is closed.</p>
 */
public final class ContainerExec {

    private ContainerExec() {
    }

    public record Result(
            int exitCode,
            String stdout,
            String stderr,
            boolean truncated,
            boolean timedOut,
            String error
    ) {
        public boolean succeeded() {
            return !timedOut && error == null && exitCode == 0;
        }
    }

    public static Result run(
            DockerClient client,
            String containerId,
            List<String> command,
            String workingDir,
            long timeoutMs,
            int maxOutputBytes
    ) {
        return run(client, containerId, command, workingDir, timeoutMs, maxOutputBytes, null);
    }

    /**
     * Runs a command with a bounded stdin payload. This is used to transfer the
     * submitted source into the container: the Docker archive/copy API refuses
     * to write into a container whose root filesystem is read-only, even when
     * the destination is a writable tmpfs, so {@code cat > file} is the only
     * path that works with the hardened executor configuration.
     */
    public static Result run(
            DockerClient client,
            String containerId,
            List<String> command,
            String workingDir,
            long timeoutMs,
            int maxOutputBytes,
            byte[] stdin
    ) {
        if (timeoutMs < 1) {
            throw new IllegalArgumentException("timeoutMs must be positive");
        }

        ExecCreateCmdResponse created;
        try {
            created = client.execCreateCmd(containerId)
                    .withAttachStdin(stdin != null)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(false)
                    .withWorkingDir(workingDir)
                    .withCmd(command.toArray(String[]::new))
                    .exec();
        } catch (Exception e) {
            return new Result(-1, "", "", false, false, "Could not create exec: " + rootMessage(e));
        }

        BoundedOutputStream stdout = new BoundedOutputStream(maxOutputBytes);
        BoundedOutputStream stderr = new BoundedOutputStream(maxOutputBytes);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        ResultCallback.Adapter<Frame> callback = new ResultCallback.Adapter<>() {
            @Override
            public void onNext(Frame frame) {
                if (frame == null || frame.getPayload() == null) {
                    return;
                }
                if (frame.getStreamType() == StreamType.STDERR) {
                    stderr.write(frame.getPayload(), 0, frame.getPayload().length);
                } else if (frame.getStreamType() != StreamType.STDIN) {
                    stdout.write(frame.getPayload(), 0, frame.getPayload().length);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                failure.compareAndSet(null, throwable);
                super.onError(throwable);
            }
        };

        try {
            ExecStartCmd start = client.execStartCmd(created.getId());
            if (stdin != null) {
                start.withStdIn(new ByteArrayInputStream(stdin));
            }
            start.exec(callback);
            boolean finished = callback.awaitCompletion(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                return new Result(
                        -1,
                        stdout.text(),
                        stderr.text(),
                        stdout.isTruncated() || stderr.isTruncated(),
                        true,
                        "Execution timed out after " + timeoutMs + "ms");
            }
            if (failure.get() != null) {
                return new Result(
                        -1,
                        stdout.text(),
                        stderr.text(),
                        stdout.isTruncated() || stderr.isTruncated(),
                        false,
                        "Docker exec stream failed: " + rootMessage(failure.get()));
            }

            Long exitCode = client.inspectExecCmd(created.getId()).exec().getExitCodeLong();
            return new Result(
                    exitCode == null ? -1 : exitCode.intValue(),
                    stdout.text(),
                    stderr.text(),
                    stdout.isTruncated() || stderr.isTruncated(),
                    false,
                    null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, stdout.text(), stderr.text(),
                    stdout.isTruncated() || stderr.isTruncated(), true, "Interrupted while waiting for exec");
        } catch (Exception e) {
            return new Result(-1, stdout.text(), stderr.text(),
                    stdout.isTruncated() || stderr.isTruncated(), false,
                    "Docker exec failed: " + rootMessage(e));
        } finally {
            try {
                callback.close();
            } catch (IOException ignored) {
                // The stream is already closed on the success path.
            }
        }
    }

    public static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }
}
