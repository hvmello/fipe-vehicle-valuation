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
import java.util.List;
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
 * <p>Uses Caffeine {@link AsyncCache} so the reactive types are cached correctly: only the
 * <em>completed</em> result is stored, and failed lookups are not cached (Caffeine drops
 * exceptionally-completed futures). {@code @Cacheable} is avoided as it caches the publisher, not
 * the value.
 */
@Component
@Primary
public class CachingFipeClient implements FipeClient {

    private final FipeClient delegate;
    private final CurrentReferenceProvider referenceProvider;
    private final AsyncCache<String, List<FipeReference>> catalogCache;
    private final AsyncCache<String, FipeVehiclePrice> priceCache;

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

    private static <V> AsyncCache<String, V> newCache(java.time.Duration ttl, long maximumSize) {
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
        return Mono.fromFuture(priceCache.get(key,
                (k, executor) -> delegate.yearPrice(type, brandId, modelId, yearCode).toFuture()));
    }

    private Flux<FipeReference> cachedList(String key, Supplier<Flux<FipeReference>> loader) {
        return Mono.fromFuture(catalogCache.get(key, (k, executor) -> loader.get().collectList().toFuture()))
                .flatMapMany(Flux::fromIterable);
    }
}
