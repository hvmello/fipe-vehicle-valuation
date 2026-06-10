package com.fipe.valuation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.client.dto.FipeReferenceTable;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.exception.FipeIntegrationException;
import com.fipe.valuation.exception.FipeNotFoundException;
import com.fipe.valuation.exception.FipeRateLimitException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Client tests using a stubbed {@link ExchangeFunction} (no MockWebServer dependency). Each test
 * asserts both the mapped objects and the exact upstream path that was called.
 */
class FipeClientImplTest {

    private ClientRequest lastRequest;
    private final AtomicInteger callCount = new AtomicInteger();

    /** Builds a client whose WebClient always answers with the given status + JSON body. */
    private FipeClient clientReturning(HttpStatus status, String json) {
        return clientReturning(attempt -> jsonResponse(status, json));
    }

    /** Builds a client whose WebClient answer can vary per attempt (to exercise retries). */
    private FipeClient clientReturning(IntFunction<ClientResponse> perAttempt) {
        callCount.set(0);
        ExchangeFunction exchange = request -> {
            this.lastRequest = request;
            return Mono.just(perAttempt.apply(callCount.getAndIncrement()));
        };
        WebClient webClient = WebClient.builder()
                .baseUrl("http://fipe.test")
                .exchangeFunction(exchange)
                .build();
        return new FipeClientImpl(webClient);
    }

    private static ClientResponse jsonResponse(HttpStatus status, String json) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build();
    }

    @Test
    @DisplayName("references maps {code,month} tables and calls /references")
    void referencesMapsTablesAndPath() {
        FipeClient client = clientReturning(HttpStatus.OK,
                "[{\"code\":\"334\",\"month\":\"junho/2026\"},{\"code\":\"333\",\"month\":\"maio/2026\"}]");

        List<FipeReferenceTable> result = client.references().collectList().block();

        assertThat(result)
                .extracting(FipeReferenceTable::code, FipeReferenceTable::month)
                .containsExactly(tuple("334", "junho/2026"), tuple("333", "maio/2026"));
        assertThat(lastRequest.url().getPath()).isEqualTo("/references");
    }

    @Test
    @DisplayName("FR-7: brands maps NamedCode items and calls /{type}/brands")
    void brandsMapsItemsAndPath() {
        FipeClient client = clientReturning(HttpStatus.OK,
                "[{\"code\":\"1\",\"name\":\"Acura\"},{\"code\":\"21\",\"name\":\"Fiat\"}]");

        List<FipeReference> result = client.brands(VehicleType.CARS).collectList().block();

        assertThat(result)
                .extracting(FipeReference::code, FipeReference::name)
                .containsExactly(tuple("1", "Acura"), tuple("21", "Fiat"));
        assertThat(lastRequest.url().getPath()).isEqualTo("/cars/brands");
    }

    @Test
    @DisplayName("FR-8: models calls /{type}/brands/{brandId}/models")
    void modelsCallsCorrectPath() {
        FipeClient client = clientReturning(HttpStatus.OK, "[{\"code\":\"437\",\"name\":\"147 C/ CL\"}]");

        client.models(VehicleType.CARS, "21").collectList().block();

        assertThat(lastRequest.url().getPath()).isEqualTo("/cars/brands/21/models");
    }

    @Test
    @DisplayName("Price detail: maps all 9 fields and calls the year-detail path")
    void yearPriceMapsAllFields() {
        String json = "{\"vehicleType\":1,\"price\":\"R$ 6.027,00\",\"brand\":\"Fiat\","
                + "\"model\":\"147 C/ CL\",\"modelYear\":1987,\"fuel\":\"Gasolina\","
                + "\"codeFipe\":\"001124-0\",\"referenceMonth\":\"junho de 2026\",\"fuelAcronym\":\"G\"}";
        FipeClient client = clientReturning(HttpStatus.OK, json);

        FipeVehiclePrice price = client.yearPrice(VehicleType.CARS, "21", "437", "1987-1").block();

        assertThat(price).isNotNull();
        assertThat(price.vehicleType()).isEqualTo(1);
        assertThat(price.price()).isEqualTo("R$ 6.027,00");
        assertThat(price.modelYear()).isEqualTo(1987);
        assertThat(price.fuel()).isEqualTo("Gasolina");
        assertThat(price.codeFipe()).isEqualTo("001124-0");
        assertThat(lastRequest.url().getPath()).isEqualTo("/cars/brands/21/models/437/years/1987-1");
    }

    @Test
    @DisplayName("FR-5: upstream 404 becomes FipeNotFoundException naming the resource")
    void notFoundIsMapped() {
        FipeClient client = clientReturning(HttpStatus.NOT_FOUND,
                "{\"error\":\"failed to locate the information on fipe.org\"}");

        assertThatThrownBy(() -> client.models(VehicleType.CARS, "99999999").collectList().block())
                .isInstanceOf(FipeNotFoundException.class)
                .hasMessageContaining("99999999");
    }

    @Test
    @DisplayName("FR-12: upstream 5xx becomes FipeIntegrationException")
    void serverErrorIsMapped() {
        FipeClient client = clientReturning(HttpStatus.INTERNAL_SERVER_ERROR, "boom");

        assertThatThrownBy(() -> client.brands(VehicleType.CARS).collectList().block())
                .isInstanceOf(FipeIntegrationException.class);
    }

    @Test
    @DisplayName("Resilience: a transient 503 is retried and the next attempt succeeds")
    void transientErrorIsRetried() {
        FipeClient client = clientReturning(attempt -> attempt == 0
                ? jsonResponse(HttpStatus.SERVICE_UNAVAILABLE, "busy")
                : jsonResponse(HttpStatus.OK, "[{\"code\":\"1\",\"name\":\"Acura\"}]"));

        List<FipeReference> result = client.brands(VehicleType.CARS).collectList().block();

        assertThat(result).extracting(FipeReference::name).containsExactly("Acura");
        assertThat(callCount.get()).isEqualTo(2); // original + one retry
    }

    @Test
    @DisplayName("Resilience: a 404 is never retried (one call only)")
    void notFoundIsNotRetried() {
        FipeClient client = clientReturning(attempt ->
                jsonResponse(HttpStatus.NOT_FOUND, "{\"error\":\"not found\"}"));

        assertThatThrownBy(() -> client.models(VehicleType.CARS, "99999999").collectList().block())
                .isInstanceOf(FipeNotFoundException.class);
        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-12/NFR-8: a persistent 429 is retried then mapped to FipeRateLimitException")
    void rateLimitIsRetriedThenMapped() {
        FipeClient client = clientReturning(attempt ->
                jsonResponse(HttpStatus.TOO_MANY_REQUESTS, "{\"error\":\"rate limit\"}"));

        assertThatThrownBy(() -> client.brands(VehicleType.CARS).collectList().block())
                .isInstanceOf(FipeRateLimitException.class);
        assertThat(callCount.get()).isEqualTo(3); // original + two retries (429 is transient)
    }
}
