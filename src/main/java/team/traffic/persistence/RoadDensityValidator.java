package team.traffic.persistence;

import java.util.Locale;

final class RoadDensityValidator {
    private static final double DENSITY_TOLERANCE = 1.0e-3;

    private RoadDensityValidator() {
    }

    static void validate(String roadId, int capacity, int currentVehicleCount, double vehicleDensity) {
        if (capacity <= 0 || currentVehicleCount < 0 || currentVehicleCount > capacity) {
            return;
        }

        double expectedDensity = (double) currentVehicleCount / capacity;
        if (Math.abs(expectedDensity - vehicleDensity) > DENSITY_TOLERANCE) {
            throw new IllegalArgumentException(String.format(
                    Locale.ROOT,
                    "Vehicle density for road %s must match currentVehicleCount / capacity "
                            + "(expected %.3f but was %.3f)",
                    roadId,
                    expectedDensity,
                    vehicleDensity));
        }
    }
}
