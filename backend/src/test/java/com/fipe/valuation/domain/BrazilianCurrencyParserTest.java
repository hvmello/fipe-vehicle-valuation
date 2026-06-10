package com.fipe.valuation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** FR-4 / NFR-1: pt-BR currency parsing to an exact, scale-2 {@link BigDecimal}. */
class BrazilianCurrencyParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
            "'R$ 6.027,00', 6027.00",
            "'R$ 18.225,00', 18225.00",
            "'R$ 88.890,00', 88890.00",
            "'R$ 240.932,00', 240932.00",
            "'R$ 1.234.567,89', 1234567.89",
            "'R$ 0,00', 0.00",
    })
    @DisplayName("FR-4: parses valid BRL strings to scale-2 BigDecimal")
    void parsesValidStrings(String raw, BigDecimal expected) {
        BigDecimal result = BrazilianCurrencyParser.parse(raw);
        assertThat(result).isEqualTo(expected);
        assertThat(result.scale()).isEqualTo(2);
    }

    @Test
    @DisplayName("FR-4: tolerates a non-breaking space after R$")
    void parsesNonBreakingSpaceVariant() {
        assertThat(BrazilianCurrencyParser.parse("R$ 6.027,00")).isEqualTo(new BigDecimal("6027.00"));
    }

    @Test
    @DisplayName("FR-4: parses an amount with no thousands grouping")
    void parsesWithoutGrouping() {
        assertThat(BrazilianCurrencyParser.parse("R$ 6027,00")).isEqualTo(new BigDecimal("6027.00"));
    }

    @Test
    @DisplayName("NFR-1: rounds HALF_UP to two decimals")
    void roundsHalfUp() {
        assertThat(BrazilianCurrencyParser.parse("R$ 2,005")).isEqualTo(new BigDecimal("2.01"));
    }

    @ParameterizedTest(name = "rejects \"{0}\"")
    @ValueSource(strings = {"   ", "R$ ", "abc", "R$ 1.2,3,4"})
    @DisplayName("FR-4: blank or unparseable input is rejected")
    void rejectsBlankOrUnparseable(String raw) {
        assertThatThrownBy(() -> BrazilianCurrencyParser.parse(raw))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("FR-4: null is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> BrazilianCurrencyParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
