package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.vehicle.VehicleType;

/**
 * Driver-behaviour parameters that complement the physical vehicle profile.
 *
 * <p>The physical profile on {@code Vehicle} describes the machine: length,
 * width, mass, and the acceleration it can produce. This record describes the
 * person behind the wheel, which is what a microscopic traffic model actually
 * needs. The parameter names follow the conventions used by the SUMO
 * microsimulator so the values can be compared against published defaults.</p>
 *
 * @param minimumGapMetres     standstill distance kept to the vehicle in front,
 *                             in metres. SUMO calls this {@code minGap}.
 * @param reactionTimeSeconds  driver reaction time used by the car-following
 *                             model, in seconds. SUMO calls this {@code tau}.
 * @param imperfection         driver sloppiness in the range 0..1. Zero is a
 *                             perfect driver; higher values randomly give away
 *                             a fraction of the achievable acceleration. SUMO
 *                             calls this {@code sigma}.
 * @param gapAcceptanceSeconds time headway a driver requires on the conflicting
 *                             stream before crossing a junction, in seconds.
 * @param speedFactor          multiplier applied to the posted speed limit,
 *                             modelling drivers who habitually travel slightly
 *                             above or below the limit.
 */
public record DriverProfile(
        double minimumGapMetres,
        double reactionTimeSeconds,
        double imperfection,
        double gapAcceptanceSeconds,
        double speedFactor) {

    public DriverProfile {
        requireNonNegative("Minimum gap", minimumGapMetres);
        requirePositive("Reaction time", reactionTimeSeconds);
        if (!Double.isFinite(imperfection) || imperfection < 0 || imperfection > 1) {
            throw new IllegalArgumentException("Imperfection must be between 0 and 1.");
        }
        requireNonNegative("Gap acceptance", gapAcceptanceSeconds);
        requirePositive("Speed factor", speedFactor);
    }

    /**
     * Returns the default behaviour for a vehicle type.
     *
     * <p>Buses are more cautious and slower to react than cars. Emergency
     * vehicles react faster, accept smaller gaps, and are permitted to exceed
     * the posted limit.</p>
     */
    public static DriverProfile forType(VehicleType type) {
        if (type == null) {
            throw new IllegalArgumentException("Vehicle type is required.");
        }
        return switch (type) {
            case CAR -> new DriverProfile(2.5, 1.0, 0.5, 3.0, 1.0);
            case BUS -> new DriverProfile(3.0, 1.4, 0.4, 4.5, 0.95);
            case EMERGENCY -> new DriverProfile(2.0, 0.7, 0.1, 1.5, 1.3);
        };
    }

    /**
     * Space one stopped vehicle of the given length consumes in a queue,
     * including the standstill gap it keeps to the vehicle ahead.
     */
    public double jamSpacingMetres(double vehicleLengthMetres) {
        return vehicleLengthMetres + minimumGapMetres;
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must not be negative.");
        }
    }
}
