package com.fipe.valuation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import com.fipe.valuation.client.FipeClient;
import com.fipe.valuation.client.dto.FipeReference;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.config.FipeProperties;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.domain.VehicleValuation;
import com.fipe.valuation.domain.YearValuation;
import java.math.BigDecimal;
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

@ExtendWith(MockitoExtension.class)
class VehicleValuationServiceTest {

    private static final String BRAND = "21";
    private static final String MODEL = "8";

    @Mock
    private FipeClient client;

    private VehicleValuationService service;

    @BeforeEach
    void setUp() {
        FipeProperties props = new FipeProperties("http://fipe.test", "",
                Duration.ofSeconds(5), Duration.ofSeconds(10), 8,
                new FipeProperties.Cors(List.of("http://localhost:4200")),
                new FipeProperties.Cache(
                        new FipeProperties.Cache.Catalog(Duration.ofDays(7), 2000),
                        new FipeProperties.Cache.Price(Duration.ofHours(24), 20_000),
                        Duration.ofHours(24)));
        service = new VehicleValuationService(client, props);
    }

    private FipeReference ref(String code) {
        return new FipeReference(code, code);
    }

    private FipeVehiclePrice price(int modelYear, String fuel, String priceStr) {
        return new FipeVehiclePrice(VehicleType.CARS.fipeId(), priceStr, "Fiat", "XPTO",
                modelYear, fuel, "001124-0", "junho de 2026", "G");
    }

    private void stubYear(String code, int modelYear, String fuel, String priceStr) {
        when(client.yearPrice(VehicleType.CARS, BRAND, MODEL, code))
                .thenReturn(Mono.just(price(modelYear, fuel, priceStr)));
    }

    private VehicleValuation valuate() {
        return service.valuate(VehicleType.CARS, BRAND, MODEL).block();
    }

    @Test
    @DisplayName("FR-1/FR-2/FR-10: newest-first, chained vs previous AVAILABLE year (2013→2011)")
    void chainsAcrossSkippedYears() {
        when(client.years(VehicleType.CARS, BRAND, MODEL))
                .thenReturn(Flux.just(ref("2013-1"), ref("2011-1"), ref("2010-1"), ref("2009-1")));
        stubYear("2009-1", 2009, "Gasolina", "R$ 18.225,00");
        stubYear("2010-1", 2010, "Gasolina", "R$ 20.250,00");
        stubYear("2011-1", 2011, "Gasolina", "R$ 22.500,00");
        stubYear("2013-1", 2013, "Gasolina", "R$ 25.000,00");

        List<YearValuation> years = valuate().years();

        assertThat(years).extracting(YearValuation::year, YearValuation::previousYear)
                .containsExactly(tuple(2013, 2011), tuple(2011, 2010), tuple(2010, 2009), tuple(2009, null));
        assertThat(years).extracting(YearValuation::change).containsExactly(
                new BigDecimal("2500.00"), new BigDecimal("2250.00"), new BigDecimal("2025.00"), null);
        assertThat(years).extracting(YearValuation::changePercent).containsExactly(
                new BigDecimal("11.11"), new BigDecimal("11.11"), new BigDecimal("11.11"), null);
    }

    @Test
    @DisplayName("FR-3: a single-year model yields one entry with null change")
    void singleYearHasNoChange() {
        when(client.years(VehicleType.CARS, BRAND, MODEL)).thenReturn(Flux.just(ref("2020-1")));
        stubYear("2020-1", 2020, "Gasolina", "R$ 50.000,00");

        List<YearValuation> years = valuate().years();

        assertThat(years).singleElement().satisfies(y -> {
            assertThat(y.year()).isEqualTo(2020);
            assertThat(y.change()).isNull();
            assertThat(y.changePercent()).isNull();
            assertThat(y.previousYear()).isNull();
        });
    }

    @Test
    @DisplayName("FR-6: modelYear 32000 is labelled '0 km' and sorted newest")
    void zeroKmIsNewestAndLabelled() {
        when(client.years(VehicleType.CARS, BRAND, MODEL))
                .thenReturn(Flux.just(ref("32000-1"), ref("2020-1")));
        stubYear("2020-1", 2020, "Gasolina", "R$ 50.000,00");
        stubYear("32000-1", 32000, "Gasolina", "R$ 60.000,00");

        List<YearValuation> years = valuate().years();

        assertThat(years).extracting(YearValuation::year, YearValuation::label)
                .containsExactly(tuple(32000, "0 km"), tuple(2020, "2020"));
        assertThat(years.get(0).previousYear()).isEqualTo(2020);
    }

    @Test
    @DisplayName("FR-13: same-year multi-fuel (Fusca) ordered (year asc, fuelId desc) then newest-first, cross-fuel labels")
    void multiFuelSameYearChainsSequentially() {
        when(client.years(VehicleType.CARS, BRAND, MODEL)).thenReturn(Flux.just(
                ref("1996-1"), ref("1996-2"), ref("1995-1"), ref("1995-2"), ref("1994-1")));
        stubYear("1994-1", 1994, "Gasolina", "R$ 10.000,00");
        stubYear("1995-1", 1995, "Gasolina", "R$ 11.000,00");
        stubYear("1995-2", 1995, "Álcool", "R$ 9.000,00");
        stubYear("1996-1", 1996, "Gasolina", "R$ 12.000,00");
        stubYear("1996-2", 1996, "Álcool", "R$ 9.500,00");

        List<YearValuation> years = valuate().years();

        // Newest-first; within a year, Gasolina (fuelId 1) before Álcool (fuelId 2).
        assertThat(years).extracting(YearValuation::year, YearValuation::fuel).containsExactly(
                tuple(1996, "Gasolina"), tuple(1996, "Álcool"), tuple(1995, "Gasolina"),
                tuple(1995, "Álcool"), tuple(1994, "Gasolina"));
        // 1996 Gasolina is compared to 1996 Álcool → previousLabel qualified by fuel, same year.
        assertThat(years.get(0).previousYear()).isEqualTo(1996);
        assertThat(years.get(0).previousLabel()).isEqualTo("1996 Álcool");
        // Oldest (1994) has no comparison.
        assertThat(years.get(4).change()).isNull();
    }

    @Test
    @DisplayName("Divide-by-zero guard: previous price 0 → null percent, change still computed")
    void zeroPreviousPriceYieldsNullPercent() {
        when(client.years(VehicleType.CARS, BRAND, MODEL))
                .thenReturn(Flux.just(ref("2019-1"), ref("2018-1")));
        stubYear("2018-1", 2018, "Gasolina", "R$ 0,00");
        stubYear("2019-1", 2019, "Gasolina", "R$ 1.000,00");

        YearValuation newest = valuate().years().get(0);

        assertThat(newest.change()).isEqualByComparingTo("1000.00");
        assertThat(newest.changePercent()).isNull();
    }

    @Test
    @DisplayName("Negative variation is preserved (depreciation)")
    void negativeChangeIsPreserved() {
        when(client.years(VehicleType.CARS, BRAND, MODEL))
                .thenReturn(Flux.just(ref("2019-1"), ref("2018-1")));
        stubYear("2018-1", 2018, "Gasolina", "R$ 20.000,00");
        stubYear("2019-1", 2019, "Gasolina", "R$ 18.000,00");

        YearValuation newest = valuate().years().get(0);

        assertThat(newest.change()).isEqualByComparingTo("-2000.00");
        assertThat(newest.changePercent()).isEqualByComparingTo("-10.00");
    }

    @Test
    @DisplayName("Metadata (brand/model/fipeCode/referenceMonth) is surfaced from FIPE")
    void exposesMetadata() {
        when(client.years(VehicleType.CARS, BRAND, MODEL)).thenReturn(Flux.just(ref("2020-1")));
        stubYear("2020-1", 2020, "Gasolina", "R$ 50.000,00");

        VehicleValuation result = valuate();

        assertThat(result.brand()).isEqualTo("Fiat");
        assertThat(result.model()).isEqualTo("XPTO");
        assertThat(result.fipeCode()).isEqualTo("001124-0");
        assertThat(result.referenceMonth()).isEqualTo("junho de 2026");
        assertThat(result.vehicleType()).isEqualTo(VehicleType.CARS);
    }
}
