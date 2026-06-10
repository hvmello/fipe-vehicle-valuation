package com.fipe.valuation.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;
import java.util.Locale;

/**
 * Parses FIPE's pt-BR currency strings (e.g. {@code "R$ 6.027,00"}, {@code "R$ 1.234.567,89"}) into
 * an exact {@link BigDecimal} (scale 2, {@link RoundingMode#HALF_UP}). Money is never represented as
 * a floating-point number (NFR-1). See specs/001-vehicle-valuation/data-model.md §4.
 *
 * <p>Numbers are parsed with a locale-aware {@link DecimalFormat} configured for the pt-BR
 * convention ('.' groups thousands, ',' is the decimal separator) rather than by hand-rolled string
 * surgery. The separators are pinned explicitly so parsing does not depend on the JVM's default
 * locale — FIPE always emits pt-BR regardless of where this service runs. The {@link ParsePosition}
 * is checked to ensure the <em>whole</em> number was consumed, so malformed input (e.g.
 * {@code "1.2,3,4"}) is rejected rather than silently truncated.
 */
public final class BrazilianCurrencyParser {

    private static final int SCALE = 2;
    private static final String CURRENCY_SYMBOL = "R$";
    /** U+00A0 NON-BREAKING SPACE — FIPE sometimes separates the symbol from the amount with it. */
    private static final char NON_BREAKING_SPACE = (char) 0xA0;

    private static final DecimalFormatSymbols PT_BR_SYMBOLS = ptBrSymbols();

    /** {@link DecimalFormat} is not thread-safe; this parser runs concurrently across price calls. */
    private static final ThreadLocal<DecimalFormat> FORMAT = ThreadLocal.withInitial(() -> {
        DecimalFormat format = new DecimalFormat("#,##0.##", PT_BR_SYMBOLS);
        format.setParseBigDecimal(true);
        return format;
    });

    private BrazilianCurrencyParser() {
    }

    /**
     * @param raw a FIPE price string; the currency symbol, spaces and non-breaking spaces are ignored.
     * @return the value as {@code BigDecimal} with scale 2.
     * @throws IllegalArgumentException if {@code raw} is blank or has no parseable numeric content (FR-4).
     */
    public static BigDecimal parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Price must not be null or blank");
        }
        // Drop the currency symbol and any spacing (incl. non-breaking) so only the number remains.
        String number = raw.replace(CURRENCY_SYMBOL, "")
                .replace(NON_BREAKING_SPACE, ' ')
                .replace(" ", "")
                .strip();
        if (number.isEmpty()) {
            throw new IllegalArgumentException("No numeric content in price: '" + raw + "'");
        }

        ParsePosition position = new ParsePosition(0);
        Number parsed = FORMAT.get().parse(number, position);
        if (parsed == null || position.getIndex() != number.length()) {
            throw new IllegalArgumentException("Unparseable price: '" + raw + "'");
        }
        return ((BigDecimal) parsed).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static DecimalFormatSymbols ptBrSymbols() {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        return symbols;
    }
}
