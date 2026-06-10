package com.fipe.valuation.exception;

/**
 * Raised when FIPE returns 404 for a requested brand/model/year. Mapped to HTTP 404 by the web
 * layer (FR-5).
 */
public class FipeNotFoundException extends RuntimeException {

    public FipeNotFoundException(String message) {
        super(message);
    }
}
