package com.aegis.code_sandbox.exception;

/**
 * Raised when the sandbox cannot establish or keep a connection to the Docker
 * Engine, or when the executor image/pool cannot be prepared.
 *
 * <p>The message is intentionally actionable: in Pterodactyl this text is the
 * first thing an operator sees when the service refuses to start.</p>
 */
public class DockerUnavailableException extends RuntimeException {

    public DockerUnavailableException(String message) {
        super(message);
    }

    public DockerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
