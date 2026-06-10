package com.fipe.valuation;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerResponse;

/**
 * End-to-end integration test: the full Spring context calls a stubbed FIPE server (a real
 * reactor-netty HTTP server on a random port) via the configured {@code WebClient}. Proves the
 * worked example (FR-1) and the 404 mapping (FR-5) through every layer.
 *
 * <p>Named {@code *IntegrationTests} (not {@code *IT}) so it runs under Surefire in the {@code test}
 * phase — this project has no Failsafe binding (offline build).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class FipeValuationIntegrationTests {

    private static final String YEARS_JSON = """
            [{"code":"2013-1","name":"2013 Gasolina"},{"code":"2011-1","name":"2011 Gasolina"},
             {"code":"2010-1","name":"2010 Gasolina"},{"code":"2009-1","name":"2009 Gasolina"}]""";

    /** Started at class load so its port is available when @DynamicPropertySource is resolved. */
    private static final DisposableServer FIPE_STUB = startStub();

    @Autowired
    private WebTestClient web;

    private static DisposableServer startStub() {
        return HttpServer.create().port(0)
                .route(routes -> routes
                        .get("/cars/brands/21/models/437/years", (req, res) ->
                                json(res, 200, YEARS_JSON))
                        .get("/cars/brands/21/models/437/years/{yearId}", (req, res) ->
                                json(res, 200, priceJson(req.param("yearId"))))
                        .get("/cars/brands/999/models", (req, res) ->
                                json(res, 404, "{\"error\":\"failed to locate the information on fipe.org\"}")))
                .bindNow();
    }

    private static org.reactivestreams.Publisher<Void> json(HttpServerResponse res, int status, String body) {
        return res.status(status).header("Content-Type", "application/json").sendString(Mono.just(body));
    }

    private static String priceJson(String yearId) {
        int year = Integer.parseInt(yearId.split("-")[0]);
        String price = switch (year) {
            case 2013 -> "R$ 25.000,00";
            case 2011 -> "R$ 22.500,00";
            case 2010 -> "R$ 20.250,00";
            case 2009 -> "R$ 18.225,00";
            default -> "R$ 0,00";
        };
        return "{\"vehicleType\":1,\"price\":\"" + price + "\",\"brand\":\"Fiat\",\"model\":\"147 C/ CL\","
                + "\"modelYear\":" + year + ",\"fuel\":\"Gasolina\",\"codeFipe\":\"001124-0\","
                + "\"referenceMonth\":\"junho de 2026\",\"fuelAcronym\":\"G\"}";
    }

    @DynamicPropertySource
    static void fipeProperties(DynamicPropertyRegistry registry) {
        registry.add("fipe.base-url", () -> "http://localhost:" + FIPE_STUB.port());
    }

    @AfterAll
    static void stopStub() {
        FIPE_STUB.disposeNow();
    }

    @Test
    @DisplayName("FR-1: full chain returns the worked-example deltas, newest-first")
    void valuationWorkedExample() {
        web.get().uri("/api/v1/cars/brands/21/models/437/valuation").exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.brand").isEqualTo("Fiat")
                .jsonPath("$.currency").isEqualTo("BRL")
                .jsonPath("$.years.length()").isEqualTo(4)
                .jsonPath("$.years[0].year").isEqualTo(2013)
                .jsonPath("$.years[0].previousYear").isEqualTo(2011)
                .jsonPath("$.years[0].change").isEqualTo(2500.00)
                .jsonPath("$.years[0].changePercent").isEqualTo(11.11)
                .jsonPath("$.years[1].year").isEqualTo(2011)
                .jsonPath("$.years[2].year").isEqualTo(2010)
                .jsonPath("$.years[3].year").isEqualTo(2009)
                .jsonPath("$.years[3].change").doesNotExist();
    }

    @Test
    @DisplayName("FR-5: upstream 404 surfaces as a 404 ProblemDetail")
    void notFoundIsPropagated() {
        web.get().uri("/api/v1/cars/brands/999/models").exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.status").isEqualTo(404);
    }
}
