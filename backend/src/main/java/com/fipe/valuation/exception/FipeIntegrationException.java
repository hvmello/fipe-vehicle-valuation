package com.fipe.valuation.exception;

/**
 * Raised when FIPE is unreachable, times out, is rate-limited (429) or returns 5xx. Mapped to HTTP
 * 502/503 by the web layer (FR-12).
 */
public class FipeIntegrationException extends RuntimeException {

    public FipeIntegrationException(String message) {
        super(message);
    }

    public FipeIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
