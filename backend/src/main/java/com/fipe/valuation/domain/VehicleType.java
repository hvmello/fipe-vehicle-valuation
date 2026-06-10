package com.fipe.valuation.domain;

/**
 * FIPE vehicle type. {@code path} is the URL segment used by both our API and FIPE; {@code fipeId}
 * is the integer FIPE reports in its price object's {@code vehicleType} field.
 *
 * <p>The {@code fipeId} mapping was verified live (see specs/001-vehicle-valuation §7): 1=cars,
 * 2=motorcycles, 3=trucks.
 */
public enum VehicleType {

    CARS("cars", 1),
    MOTORCYCLES("motorcycles", 2),
    TRUCKS("trucks", 3);

    private final String path;
    private final int fipeId;

    VehicleType(String path, int fipeId) {
        this.path = path;
        this.fipeId = fipeId;
    }

    public String path() {
        return path;
    }

    public int fipeId() {
        return fipeId;
    }

    /**
     * Resolves a type from its URL segment, case-insensitively (FR-11).
     *
     * @throws IllegalArgumentException if no type matches (mapped to HTTP 400 by the web layer).
     */
    public static VehicleType fromPath(String value) {
        for (VehicleType type : values()) {
            if (type.path.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown vehicle type '" + value + "'");
    }

    /**
     * Resolves a type from FIPE's integer {@code vehicleType}, used for the integrity check (FR-14).
     *
     * @throws IllegalArgumentException if the integer matches no known type.
     */
    public static VehicleType fromFipeId(int fipeId) {
        for (VehicleType type : values()) {
            if (type.fipeId == fipeId) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown FIPE vehicle type id " + fipeId);
    }
}
