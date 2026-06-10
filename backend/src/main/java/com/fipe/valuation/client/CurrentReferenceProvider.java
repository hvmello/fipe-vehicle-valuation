package com.fipe.valuation.client;

import com.fipe.valuation.client.dto.FipeReferenceTable;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Tracks FIPE's <em>current</em> reference-table code (the monthly price snapshot). The code is used
 * as a cache-generation token for prices, so when FIPE publishes a new month the price cache rolls
 * over. Resolved eagerly at startup and re-checked on a schedule (see {@code SchedulingConfig}); a
 * detected change fires registered listeners so the price cache can be invalidated (freeing the old
 * month's entries) — the "FIPE update is a trigger" requirement.
 *
 * <p>Depends on the raw {@link FipeClientImpl} (not the caching wrapper) to avoid a DI cycle, since
 * {@code CachingFipeClient} depends on this provider.
 */
@Component
public class CurrentReferenceProvider {

    private static final Logger log = LoggerFactory.getLogger(CurrentReferenceProvider.class);

    /** Used until the first successful resolution; still a valid (if generic) cache-key token. */
    static final String UNKNOWN = "latest";

    private final FipeClientImpl delegate;
    private final AtomicReference<String> currentCode = new AtomicReference<>(UNKNOWN);
    private final List<Runnable> changeListeners = new CopyOnWriteArrayList<>();

    public CurrentReferenceProvider(FipeClientImpl delegate) {
        this.delegate = delegate;
    }

    /** The current reference code, or {@code "latest"} until the first resolution. */
    public String currentReferenceCode() {
        return currentCode.get();
    }

    /** Registers a callback fired whenever the reference code changes (e.g. a monthly rollover). */
    public void onReferenceChange(Runnable listener) {
        changeListeners.add(listener);
    }

    @EventListener(ApplicationReadyEvent.class)
    void resolveOnStartup() {
        refresh();
    }

    /** Re-resolves the latest reference code; on failure keeps the last known value. */
    public void refresh() {
        delegate.references()
                .map(FipeReferenceTable::code)
                .filter(code -> code != null && !code.isBlank())
                .collectList()
                .subscribe(this::applyLatest,
                        error -> log.warn("Could not refresh FIPE reference table; keeping '{}': {}",
                                currentCode.get(), error.toString()));
    }

    private void applyLatest(List<String> codes) {
        codes.stream().max(Comparator.comparingInt(CurrentReferenceProvider::asInt)).ifPresent(latest -> {
            String previous = currentCode.getAndSet(latest);
            if (!latest.equals(previous)) {
                log.info("FIPE current reference table is '{}' (was '{}') — refreshing price cache", latest, previous);
                changeListeners.forEach(Runnable::run);
            }
        });
    }

    /** Reference codes are numeric strings; non-numeric sort lowest so a valid code always wins. */
    private static int asInt(String code) {
        try {
            return Integer.parseInt(code.trim());
        } catch (NumberFormatException ex) {
            return Integer.MIN_VALUE;
        }
    }
}
