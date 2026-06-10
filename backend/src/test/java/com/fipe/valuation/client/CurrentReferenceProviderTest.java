package com.fipe.valuation.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fipe.valuation.client.dto.FipeReferenceTable;
import com.fipe.valuation.exception.FipeIntegrationException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

/** Resolves FIPE's current reference table and fires a change hook only on a real rollover. */
@ExtendWith(MockitoExtension.class)
class CurrentReferenceProviderTest {

    @Mock
    private FipeClientImpl delegate;

    private CurrentReferenceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new CurrentReferenceProvider(delegate);
    }

    @Test
    @DisplayName("Resolves the latest (highest) reference code")
    void resolvesLatestCode() {
        when(delegate.references()).thenReturn(Flux.just(
                new FipeReferenceTable("333", "maio/2026"),
                new FipeReferenceTable("334", "junho/2026")));

        assertThat(provider.currentReferenceCode()).isEqualTo(CurrentReferenceProvider.UNKNOWN);
        provider.refresh();
        assertThat(provider.currentReferenceCode()).isEqualTo("334");
    }

    @Test
    @DisplayName("Fires the change listener only when the code actually changes")
    void firesListenerOnlyOnChange() {
        when(delegate.references()).thenReturn(
                Flux.just(new FipeReferenceTable("334", "junho/2026")),  // sentinel -> 334 (change)
                Flux.just(new FipeReferenceTable("334", "junho/2026")),  // 334 -> 334 (no change)
                Flux.just(new FipeReferenceTable("335", "julho/2026"))); // 334 -> 335 (change)
        AtomicInteger fired = new AtomicInteger();
        provider.onReferenceChange(fired::incrementAndGet);

        provider.refresh();
        provider.refresh();
        provider.refresh();

        assertThat(fired.get()).isEqualTo(2);
        assertThat(provider.currentReferenceCode()).isEqualTo("335");
    }

    @Test
    @DisplayName("Keeps the last known code when a refresh fails")
    void keepsLastKnownOnError() {
        when(delegate.references()).thenReturn(
                Flux.just(new FipeReferenceTable("334", "junho/2026")),
                Flux.error(new FipeIntegrationException("FIPE down")));
        AtomicInteger fired = new AtomicInteger();
        provider.onReferenceChange(fired::incrementAndGet);

        provider.refresh();
        provider.refresh(); // fails

        assertThat(provider.currentReferenceCode()).isEqualTo("334");
        assertThat(fired.get()).isEqualTo(1); // only the first resolution fired
    }
}
