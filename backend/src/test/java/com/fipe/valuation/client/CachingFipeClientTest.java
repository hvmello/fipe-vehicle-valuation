package com.fipe.valuation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.client.dto.FipeReferenceTable;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.config.FipeProperties;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.exception.FipeIntegrationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/** NFR-4: the caching decorator serves repeat reads from cache and does not cache failures. */
@ExtendWith(MockitoExtension.class)
class CachingFipeClientTest {

    @Mock
    private FipeClientImpl delegate;

    private CachingFipeClient cachingClient;
    private CurrentReferenceProvider referenceProvider;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        FipeProperties props = new FipeProperties("http://fipe.test", "",
                Duration.ofSeconds(5), Duration.ofSeconds(10), 8,
                new FipeProperties.Cors(List.of("http://localhost:4200")),
                new FipeProperties.Cache(
                        new FipeProperties.Cache.Catalog(Duration.ofDays(7), 1000),
                        new FipeProperties.Cache.Price(Duration.ofMinutes(10), 1000),
                        Duration.ofHours(24)));
        meterRegistry = new SimpleMeterRegistry();
        referenceProvider = new CurrentReferenceProvider(delegate);
        cachingClient = new CachingFipeClient(delegate, props, referenceProvider, meterRegistry);
    }

    @Test
    @DisplayName("A second read is served from cache (delegate hit once)")
    void cachesSuccessfulReads() {
        when(delegate.brands(VehicleType.CARS))
                .thenReturn(Flux.just(new FipeReference("1", "Acura")));

        cachingClient.brands(VehicleType.CARS).collectList().block();
        List<FipeReference> second = cachingClient.brands(VehicleType.CARS).collectList().block();

        assertThat(second).extracting(FipeReference::name).containsExactly("Acura");
        verify(delegate, times(1)).brands(VehicleType.CARS);
    }

    @Test
    @DisplayName("Failures are not cached: a later call retries the delegate")
    void doesNotCacheFailures() {
        when(delegate.brands(VehicleType.CARS))
                .thenReturn(Flux.error(new FipeIntegrationException("boom")))
                .thenReturn(Flux.just(new FipeReference("1", "Acura")));

        assertThatThrownBy(() -> cachingClient.brands(VehicleType.CARS).collectList().block())
                .isInstanceOf(FipeIntegrationException.class);
        List<FipeReference> retry = cachingClient.brands(VehicleType.CARS).collectList().block();

        assertThat(retry).hasSize(1);
        verify(delegate, times(2)).brands(VehicleType.CARS);
    }

    @Test
    @DisplayName("NFR-4: Caffeine cache metrics are registered for observability")
    void registersCacheMetrics() {
        assertThat(meterRegistry.find("cache.gets").tag("cache", "fipe.catalog").meters())
                .as("catalog cache metrics").isNotEmpty();
        assertThat(meterRegistry.find("cache.gets").tag("cache", "fipe.prices").meters())
                .as("price cache metrics").isNotEmpty();
    }

    @Test
    @DisplayName("NFR-4: a new FIPE reference invalidates the price cache (prices re-fetched)")
    void priceCacheBustsOnReferenceChange() {
        when(delegate.yearPrice(VehicleType.CARS, "21", "437", "1987-1"))
                .thenReturn(Mono.just(price("R$ 6.027,00")));

        cachingClient.yearPrice(VehicleType.CARS, "21", "437", "1987-1").block();
        cachingClient.yearPrice(VehicleType.CARS, "21", "437", "1987-1").block(); // served from cache

        // Simulate FIPE publishing a new monthly table → change trigger fires → price cache cleared.
        when(delegate.references()).thenReturn(Flux.just(new FipeReferenceTable("999", "novo/2026")));
        referenceProvider.refresh();

        cachingClient.yearPrice(VehicleType.CARS, "21", "437", "1987-1").block(); // must re-fetch

        verify(delegate, times(2)).yearPrice(VehicleType.CARS, "21", "437", "1987-1");
    }

    @Test
    @DisplayName("NFR-4: a reference change does NOT evict the catalog cache")
    void catalogSurvivesReferenceChange() {
        when(delegate.brands(VehicleType.CARS)).thenReturn(Flux.just(new FipeReference("1", "Acura")));

        cachingClient.brands(VehicleType.CARS).collectList().block();

        when(delegate.references()).thenReturn(Flux.just(new FipeReferenceTable("999", "novo/2026")));
        referenceProvider.refresh();

        cachingClient.brands(VehicleType.CARS).collectList().block(); // still cached

        verify(delegate, times(1)).brands(VehicleType.CARS);
    }

    private static FipeVehiclePrice price(String value) {
        return new FipeVehiclePrice(1, value, "Fiat", "147 C/ CL", 1987, "Gasolina",
                "001124-0", "junho de 2026", "G");
    }
}
