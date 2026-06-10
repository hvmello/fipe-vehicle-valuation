package com.fipe.valuation.client;

import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.client.dto.FipeReferenceTable;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.config.FipeProperties;
import com.fipe.valuation.domain.VehicleType;
import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.cache.CaffeineCacheMetrics;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Caching decorator over {@link FipeClientImpl} (NFR-4). FIPE data changes monthly, so caching cuts
 * latency and — crucially — keeps us under the daily rate limit (NFR-8), since one valuation issues
 * N price calls.
 *
 * <p>Two bounded caches keep only the hot working set in RAM (Caffeine W-TinyLFU evicts cold
 * entries): a long-lived <b>catalog</b> cache (brands/models/years) and a <b>price</b> cache whose
 * key carries the current reference code, so when FIPE rolls to a new month the
 * {@link CurrentReferenceProvider} change trigger invalidates it and reads re-fetch fresh prices.
 *
 * <p>Uses Caffeine {@link AsyncCache}, but each entry is a {@link Loaded} result rather than the raw
 * value. A load that fails completes the cached future <em>normally</em> with a captured error: this
 * keeps failures out of the cache (we evict on read) <em>and</em> avoids Caffeine logging a WARN
 * stack trace for every exceptionally-completed future (expected outcomes like a 404 would otherwise
 * spam the logs). {@code @Cacheable} is avoided as it caches the publisher, not the value.
 */
@Component
@Primary
public class CachingFipeClient implements FipeClient {

    private final FipeClient delegate;
    private final CurrentReferenceProvider referenceProvider;
    private final AsyncCache<String, Loaded<List<FipeReference>>> catalogCache;
    private final AsyncCache<String, Loaded<FipeVehiclePrice>> priceCache;

    public CachingFipeClient(FipeClientImpl delegate, FipeProperties properties,
                             CurrentReferenceProvider referenceProvider, MeterRegistry meterRegistry) {
        this.delegate = delegate;
        this.referenceProvider = referenceProvider;
        this.catalogCache = newCache(properties.cache().catalog().ttl(),
                properties.cache().catalog().maximumSize());
        this.priceCache = newCache(properties.cache().price().ttl(),
                properties.cache().price().maximumSize());
        // A new FIPE reference table (monthly) drops the now-stale prices, freeing RAM immediately.
        referenceProvider.onReferenceChange(() -> priceCache.synchronous().invalidateAll());
        // Expose hit/miss/eviction stats under /actuator/metrics (cache.* tagged by name) — NFR-4.
        CaffeineCacheMetrics.monitor(meterRegistry, this.catalogCache.synchronous(), "fipe.catalog");
        CaffeineCacheMetrics.monitor(meterRegistry, this.priceCache.synchronous(), "fipe.prices");
    }

    private static <V> AsyncCache<String, Loaded<V>> newCache(Duration ttl, long maximumSize) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(ttl)
                .recordStats()
                .buildAsync();
    }

    @Override
    public Flux<FipeReferenceTable> references() {
        // Not cached here: called only by CurrentReferenceProvider, which holds its own value.
        return delegate.references();
    }

    @Override
    public Flux<FipeReference> brands(VehicleType type) {
        return cachedList("brands:" + type.path(), () -> delegate.brands(type));
    }

    @Override
    public Flux<FipeReference> models(VehicleType type, String brandId) {
        return cachedList("models:" + type.path() + ":" + brandId, () -> delegate.models(type, brandId));
    }

    @Override
    public Flux<FipeReference> years(VehicleType type, String brandId, String modelId) {
        return cachedList("years:" + type.path() + ":" + brandId + ":" + modelId,
                () -> delegate.years(type, brandId, modelId));
    }

    @Override
    public Mono<FipeVehiclePrice> yearPrice(VehicleType type, String brandId, String modelId, String yearCode) {
        // The reference code makes the key roll over monthly; combined with the change-triggered
        // invalidation, prices never go stale across a FIPE update.
        String key = "price:" + type.path() + ":" + brandId + ":" + modelId + ":" + yearCode
                + ":ref" + referenceProvider.currentReferenceCode();
        return cached(priceCache, key, () -> delegate.yearPrice(type, brandId, modelId, yearCode));
    }

    private Flux<FipeReference> cachedList(String key, Supplier<Flux<FipeReference>> loader) {
        return cached(catalogCache, key, () -> loader.get().collectList()).flatMapMany(Flux::fromIterable);
    }

    /**
     * Returns the cached value for {@code key}, loading it once on a miss (single-flight). The load is
     * wrapped so the cached future always completes normally; a failure is evicted (never cached) and
     * its error is propagated to the caller.
     */
    private static <V> Mono<V> cached(AsyncCache<String, Loaded<V>> cache, String key, Supplier<Mono<V>> loader) {
        return Mono.defer(() -> {
            CompletableFuture<Loaded<V>> future = cache.get(key, (k, executor) -> loader.get()
                    .map(Loaded::ok)
                    .onErrorResume(error -> Mono.just(Loaded.<V>failed(error)))
                    .toFuture());
            return Mono.fromFuture(future).flatMap(loaded -> {
                if (loaded.error() != null) {
                    cache.asMap().remove(key, future); // keep failures out of the cache
                    return Mono.error(loaded.error());
                }
                return Mono.just(loaded.value());
            });
        });
    }

    /** A completed load: exactly one of {@code value} / {@code error} is non-null. */
    private record Loaded<V>(V value, Throwable error) {
        static <V> Loaded<V> ok(V value) {
            return new Loaded<>(value, null);
        }

        static <V> Loaded<V> failed(Throwable error) {
            return new Loaded<>(null, error);
        }
    }
}
