package com.fipe.valuation.web;

import com.fipe.valuation.exception.FipeIntegrationException;
import com.fipe.valuation.exception.FipeNotFoundException;
import com.fipe.valuation.exception.FipeRateLimitException;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain failures to RFC-7807 {@link ProblemDetail} responses (FR-5, FR-11, FR-12, NFR-8).
 * Stack traces are never leaked; expected upstream conditions never surface as 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String ERROR_BASE = "https://fipe.valuation/errors/";

    /** FIPE 404 → 404 (FR-5). */
    @ExceptionHandler(FipeNotFoundException.class)
    public ProblemDetail handleNotFound(FipeNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Resource not found", ex.getMessage());
    }

    /** Invalid vehicle type / bad parameter → 400 (FR-11). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Invalid request", ex.getMessage());
    }

    /** FIPE unreachable / 5xx / timeout → 502 (FR-12). */
    @ExceptionHandler(FipeIntegrationException.class)
    public ProblemDetail handleUpstream(FipeIntegrationException ex) {
        log.warn("FIPE integration failure: {}", ex.getMessage());
        return problem(HttpStatus.BAD_GATEWAY, "upstream", "FIPE service unavailable", ex.getMessage());
    }

    /** FIPE quota exhausted (429) → 503, distinct from a generic outage (FR-12 / NFR-8). */
    @ExceptionHandler(FipeRateLimitException.class)
    public ProblemDetail handleRateLimit(FipeRateLimitException ex) {
        log.warn("FIPE rate limit reached: {}", ex.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "rate-limit", "FIPE rate limit reached", ex.getMessage());
    }

    private ProblemDetail problem(HttpStatus status, String typeSlug, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create(ERROR_BASE + typeSlug));
        problem.setTitle(title);
        return problem;
    }
}
