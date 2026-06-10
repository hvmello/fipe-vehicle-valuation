package com.fipe.valuation.service;

import com.fipe.valuation.client.FipeClient;
import com.fipe.valuation.client.dto.FipeVehiclePrice;
import com.fipe.valuation.config.FipeProperties;
import com.fipe.valuation.domain.BrazilianCurrencyParser;
import com.fipe.valuation.domain.VehicleType;
import com.fipe.valuation.domain.VehicleValuation;
import com.fipe.valuation.domain.YearValuation;
import com.fipe.valuation.exception.FipeNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Computes a vehicle's valuation across its manufacture years (the core of Spec 001).
 *
 * <p>Pipeline: fetch the model's year codes → fetch each year's price <em>concurrently</em>
 * (bounded by {@code fipe.max-concurrency}) → parse to {@code BigDecimal} → sort ascending by
 * {@code (modelYear, fuelId desc)} → chain each entry against the previous one (sequential,
 * mixed-fuel) → reverse to newest-first. See data-model.md §5/§6 (FR-1,2,3,6,10,13; NFR-1,2).
 */
@Service
public class VehicleValuationService {

    private static final Logger log = LoggerFactory.getLogger(VehicleValuationService.class);

    private static final int SCALE = 2;
    private static final int ZERO_KM_YEAR = 32000;
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

    private final FipeClient client;
    private final int maxConcurrency;

    public VehicleValuationService(FipeClient client, FipeProperties properties) {
        this.client = client;
        this.maxConcurrency = properties.maxConcurrency();
    }

    /** Builds the newest-first valuation for the given brand + model. */
    public Mono<VehicleValuation> valuate(VehicleType type, String brandId, String modelId) {
        return client.years(type, brandId, modelId)
                .flatMap(year -> client.yearPrice(type, brandId, modelId, year.code())
                        .map(price -> toPricedYear(year.code(), price)), maxConcurrency)
                .collectList()
                .map(priced -> build(type, brandId, modelId, priced));
    }

    private VehicleValuation build(VehicleType type, String brandId, String modelId, List<PricedYear> priced) {
        if (priced.isEmpty()) {
            throw new FipeNotFoundException(
                    "No priced years for model " + modelId + " (brand " + brandId + ", " + type.path() + ")");
        }

        // Ascending by year, then fuelId descending (so after the reversal the newest year leads and,
        // within a year, the lowest fuelId — e.g. Gasolina — comes first).
        List<PricedYear> ascending = new ArrayList<>(priced);
        ascending.sort(Comparator.comparingInt(PricedYear::modelYear)
                .thenComparing(Comparator.comparingInt(PricedYear::fuelId).reversed()));

        FipeVehiclePrice meta = ascending.get(0).source();
        verifyTypeIntegrity(type, meta);

        List<YearValuation> chained = new ArrayList<>(ascending.size());
        for (int i = 0; i < ascending.size(); i++) {
            PricedYear current = ascending.get(i);
            PricedYear previous = i == 0 ? null : ascending.get(i - 1);
            chained.add(toYearValuation(current, previous));
        }
        Collections.reverse(chained); // newest-first

        return new VehicleValuation(type, meta.brand(), meta.model(), meta.codeFipe(), meta.referenceMonth(), chained);
    }

    private YearValuation toYearValuation(PricedYear current, PricedYear previous) {
        String label = label(current.modelYear());
        if (previous == null) {
            return new YearValuation(current.modelYear(), label, current.fuel(), current.price(),
                    null, null, null, null);
        }
        BigDecimal change = current.price().subtract(previous.price()).setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal changePercent = previous.price().signum() == 0
                ? null // avoid divide-by-zero; keep the absolute change
                : change.multiply(ONE_HUNDRED).divide(previous.price(), SCALE, RoundingMode.HALF_UP);
        return new YearValuation(current.modelYear(), label, current.fuel(), current.price(),
                change, changePercent, previous.modelYear(), previousLabel(current, previous));
    }

    /** Year label, or "0 km" for the FIPE 32000 sentinel. */
    private String label(int year) {
        return year == ZERO_KM_YEAR ? "0 km" : Integer.toString(year);
    }

    /** Compared-entry label, qualified by fuel only when the fuels differ (cross-fuel comparison). */
    private String previousLabel(PricedYear current, PricedYear previous) {
        String base = label(previous.modelYear());
        return current.fuel().equals(previous.fuel()) ? base : base + " " + previous.fuel();
    }

    private PricedYear toPricedYear(String yearCode, FipeVehiclePrice price) {
        return new PricedYear(price.modelYear(), parseFuelId(yearCode), price.fuel(),
                BrazilianCurrencyParser.parse(price.price()), price);
    }

    /** Extracts the fuel id from a year code such as {@code "1996-2"} (→ 2); defaults to 0 if absent. */
    private int parseFuelId(String yearCode) {
        int dash = yearCode.lastIndexOf('-');
        if (dash < 0 || dash == yearCode.length() - 1) {
            return 0;
        }
        try {
            return Integer.parseInt(yearCode.substring(dash + 1));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    /** FR-14: the path is authoritative; a mismatch with FIPE's integer is logged, not fatal. */
    private void verifyTypeIntegrity(VehicleType requested, FipeVehiclePrice price) {
        if (price.vehicleType() != requested.fipeId()) {
            log.warn("FIPE vehicleType {} does not match requested {} ({})",
                    price.vehicleType(), requested.fipeId(), requested.path());
        }
    }

    /** Intermediate carrying the parsed price plus the data needed to sort and to build metadata. */
    private record PricedYear(int modelYear, int fuelId, String fuel, BigDecimal price, FipeVehiclePrice source) {
    }
}
