package com.fipe.valuation.client;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.client.dto.FipeReferenceTable;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.exception.FipeIntegrationException;
import com.fipe.valuation.exception.FipeNotFoundException;
import java.time.Duration;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * {@link FipeClient} backed by the configured FIPE {@link WebClient}.
 *
 * <p>Transport failures are normalised into domain exceptions: FIPE 404 → {@link
 * FipeNotFoundException}; any other error (5xx, 429, timeout, connection, decode) → {@link
 * FipeIntegrationException}. The web layer maps those to the appropriate HTTP status (FR-5/FR-12).
 */
@Component
public class FipeClientImpl implements FipeClient {

    /**
     * FIPE's free tier intermittently resets connections / rate-limits (429). A short backoff retry
     * turns those transient blips into successes without hammering the upstream; 404s and other
     * client errors are never retried.
     */
    private static final Retry TRANSIENT_RETRY = Retry.backoff(2, Duration.ofMillis(300))
            .maxBackoff(Duration.ofSeconds(2))
            .filter(FipeClientImpl::isTransient)
            // Propagate the original failure (not Reactor's RetryExhaustedException) so the error
            // mapping below still classifies it correctly.
            .onRetryExhaustedThrow((spec, signal) -> signal.failure());

    private final WebClient webClient;

    public FipeClientImpl(WebClient fipeWebClient) {
        this.webClient = fipeWebClient;
    }

    @Override
    public Flux<FipeReferenceTable> references() {
        return webClient.get()
                .uri("/references")
                .retrieve()
                .bodyToFlux(FipeReferenceTable.class)
                .retryWhen(TRANSIENT_RETRY)
                .onErrorMap(translate("references"));
    }

    @Override
    public Flux<FipeReference> brands(VehicleType type) {
        return webClient.get()
                .uri("/{type}/brands", type.path())
                .retrieve()
                .bodyToFlux(FipeReference.class)
                .retryWhen(TRANSIENT_RETRY)
                .onErrorMap(translate("brands (" + type.path() + ")"));
    }

    @Override
    public Flux<FipeReference> models(VehicleType type, String brandId) {
        return webClient.get()
                .uri("/{type}/brands/{brandId}/models", type.path(), brandId)
                .retrieve()
                .bodyToFlux(FipeReference.class)
                .retryWhen(TRANSIENT_RETRY)
                .onErrorMap(translate("models for brand " + brandId + " (" + type.path() + ")"));
    }

    @Override
    public Flux<FipeReference> years(VehicleType type, String brandId, String modelId) {
        return webClient.get()
                .uri("/{type}/brands/{brandId}/models/{modelId}/years", type.path(), brandId, modelId)
                .retrieve()
                .bodyToFlux(FipeReference.class)
                .retryWhen(TRANSIENT_RETRY)
                .onErrorMap(translate("years for model " + modelId + " (brand " + brandId + ", " + type.path() + ")"));
    }

    @Override
    public Mono<FipeVehiclePrice> yearPrice(VehicleType type, String brandId, String modelId, String yearCode) {
        return webClient.get()
                .uri("/{type}/brands/{brandId}/models/{modelId}/years/{yearId}", type.path(), brandId, modelId, yearCode)
                .retrieve()
                .bodyToMono(FipeVehiclePrice.class)
                .retryWhen(TRANSIENT_RETRY)
                .onErrorMap(translate("price for year " + yearCode + " of model " + modelId
                        + " (brand " + brandId + ", " + type.path() + ")"));
    }

    /** Retryable: connection-level failures (reset/timeout) and FIPE 5xx/429 — never 4xx like 404. */
    private static boolean isTransient(Throwable error) {
        if (error instanceof WebClientResponseException wcre) {
            return wcre.getStatusCode().is5xxServerError() || wcre.getStatusCode().value() == 429;
        }
        return error instanceof WebClientRequestException;
    }

    /**
     * Maps a transport error to a domain exception, embedding a human-readable {@code resource}
     * description so a 404 detail can name what was missing (FR-5). Already-mapped exceptions pass
     * through unchanged.
     */
    private Function<Throwable, Throwable> translate(String resource) {
        return error -> {
            if (error instanceof FipeNotFoundException || error instanceof FipeIntegrationException) {
                return error;
            }
            if (error instanceof WebClientResponseException wcre) {
                if (wcre.getStatusCode().value() == 404) {
                    return new FipeNotFoundException("FIPE resource not found: " + resource);
                }
                return new FipeIntegrationException(
                        "FIPE returned " + wcre.getStatusCode() + " for " + resource, wcre);
            }
            return new FipeIntegrationException("Failed to reach FIPE for " + resource, error);
        };
    }
}
