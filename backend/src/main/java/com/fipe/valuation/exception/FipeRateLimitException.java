package com.fipe.valuation.exception;

/**
 * Raised when FIPE returns HTTP 429 (daily quota exhausted). Mapped to HTTP 503 by the web layer
 * with a rate-limiting message (FR-12 / NFR-8) — distinct from a generic upstream failure (502) so
 * callers can tell "try later, we're throttled" from "FIPE is broken".
 */
public class FipeRateLimitException extends RuntimeException {

    public FipeRateLimitException(String message) {
        super(message);
    }

    public FipeRateLimitException(String message, Throwable cause) {
        super(message, cause);
    }
}
