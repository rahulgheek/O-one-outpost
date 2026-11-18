package com.aegis.code_sandbox.online;

/**
 * Failure while talking to the OnlineCompiler.io API.
 *
 * <p>Messages never contain the API key or the submitted source. The HTTP
 * status is {@code -1} when the request never produced a response.</p>
 */
public class OnlineCompilerException extends RuntimeException {

    private final int statusCode;

    public OnlineCompilerException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public OnlineCompilerException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
