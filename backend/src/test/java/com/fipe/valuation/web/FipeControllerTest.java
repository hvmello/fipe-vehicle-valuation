package com.fipe.valuation.web;

import static org.mockito.Mockito.when;

import com.fipe.valuation.client.FipeClient;
import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.domain.VehicleValuation;
import com.fipe.valuation.domain.YearValuation;
import com.fipe.valuation.exception.FipeIntegrationException;
import com.fipe.valuation.exception.FipeNotFoundException;
import com.fipe.valuation.exception.FipeRateLimitException;
import com.fipe.valuation.service.VehicleValuationService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@WebFluxTest(controllers = FipeController.class)
@Import(GlobalExceptionHandler.class)
class FipeControllerTest {

    @Autowired
    private WebTestClient web;

    @MockBean
    private FipeClient fipeClient;

    @MockBean
    private VehicleValuationService valuationService;

    @Test
    @DisplayName("FR-7/FR-14: brands echo vehicleType + id + name")
    void brandsEchoType() {
        when(fipeClient.brands(VehicleType.CARS))
                .thenReturn(Flux.just(new FipeReference("21", "Fiat")));

        web.get().uri("/api/v1/cars/brands").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].vehicleType").isEqualTo("cars")
                .jsonPath("$[0].id").isEqualTo("21")
                .jsonPath("$[0].name").isEqualTo("Fiat");
    }

    @Test
    @DisplayName("FR-9: valuation response carries metadata, currency BRL and newest-first years")
    void valuationShape() {
        VehicleValuation valuation = new VehicleValuation(VehicleType.CARS, "Fiat", "147 C/ CL",
                "001124-0", "junho de 2026", List.of(
                new YearValuation(2013, "2013", "Gasolina", new BigDecimal("25000.00"),
                        new BigDecimal("2500.00"), new BigDecimal("11.11"), 2011, "2011"),
                new YearValuation(2011, "2011", "Gasolina", new BigDecimal("22500.00"),
                        null, null, null, null)));
        when(valuationService.valuate(VehicleType.CARS, "21", "437")).thenReturn(Mono.just(valuation));

        web.get().uri("/api/v1/cars/brands/21/models/437/valuation").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.vehicleType").isEqualTo("cars")
                .jsonPath("$.currency").isEqualTo("BRL")
                .jsonPath("$.fipeCode").isEqualTo("001124-0")
                .jsonPath("$.years[0].year").isEqualTo(2013)
                .jsonPath("$.years[0].changePercent").isEqualTo(11.11)
                .jsonPath("$.years[0].previousYear").isEqualTo(2011)
                .jsonPath("$.years[1].change").doesNotExist();
    }

    @Test
    @DisplayName("FR-11: unknown vehicle type → 400 ProblemDetail")
    void unknownTypeIsBadRequest() {
        web.get().uri("/api/v1/planes/brands").exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.status").isEqualTo(400)
                .jsonPath("$.title").isEqualTo("Invalid request");
    }

    @Test
    @DisplayName("FR-5: FipeNotFoundException → 404 ProblemDetail")
    void notFoundIsMapped() {
        when(fipeClient.models(VehicleType.CARS, "999"))
                .thenReturn(Flux.error(new FipeNotFoundException("No models for brand 999 (cars)")));

        web.get().uri("/api/v1/cars/brands/999/models").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404)
                .jsonPath("$.detail").isEqualTo("No models for brand 999 (cars)");
    }

    @Test
    @DisplayName("FR-12: FipeIntegrationException → 502 ProblemDetail")
    void upstreamFailureIsMapped() {
        when(valuationService.valuate(VehicleType.CARS, "21", "437"))
                .thenReturn(Mono.error(new FipeIntegrationException("FIPE returned 500 INTERNAL_SERVER_ERROR")));

        web.get().uri("/api/v1/cars/brands/21/models/437/valuation").exchange()
                .expectStatus().isEqualTo(502)
                .expectBody()
                .jsonPath("$.status").isEqualTo(502)
                .jsonPath("$.title").isEqualTo("FIPE service unavailable");
    }

    @Test
    @DisplayName("FR-12/NFR-8: FipeRateLimitException (429) → 503 ProblemDetail")
    void rateLimitIsMapped() {
        when(valuationService.valuate(VehicleType.CARS, "21", "437"))
                .thenReturn(Mono.error(new FipeRateLimitException("FIPE rate limit reached (429)")));

        web.get().uri("/api/v1/cars/brands/21/models/437/valuation").exchange()
                .expectStatus().isEqualTo(503)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("FIPE rate limit reached");
    }
}
