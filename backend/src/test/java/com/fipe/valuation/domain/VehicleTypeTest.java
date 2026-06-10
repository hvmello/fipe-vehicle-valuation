package com.fipe.valuation.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class VehicleTypeTest {

    @Test
    @DisplayName("FR-11: fromPath resolves case-insensitively")
    void fromPathIsCaseInsensitive() {
        assertThat(VehicleType.fromPath("CARS")).isEqualTo(VehicleType.CARS);
        assertThat(VehicleType.fromPath("Motorcycles")).isEqualTo(VehicleType.MOTORCYCLES);
        assertThat(VehicleType.fromPath("trucks")).isEqualTo(VehicleType.TRUCKS);
    }

    @Test
    @DisplayName("FR-11: unknown type is rejected")
    void fromPathRejectsUnknown() {
        assertThatThrownBy(() -> VehicleType.fromPath("planes"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("planes");
    }

    @Test
    @DisplayName("FR-14: fromFipeId maps the verified integers 1/2/3")
    void fromFipeIdMapsVerifiedIntegers() {
        assertThat(VehicleType.fromFipeId(1)).isEqualTo(VehicleType.CARS);
        assertThat(VehicleType.fromFipeId(2)).isEqualTo(VehicleType.MOTORCYCLES);
        assertThat(VehicleType.fromFipeId(3)).isEqualTo(VehicleType.TRUCKS);
    }

    @Test
    @DisplayName("FR-14: unknown FIPE id is rejected")
    void fromFipeIdRejectsUnknown() {
        assertThatThrownBy(() -> VehicleType.fromFipeId(99))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
