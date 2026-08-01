package team.traffic.domain.vehicle.navigation;

import team.traffic.domain.vehicle.VehicleType;

/** Strategic information, patience, hysteresis, and legal-turnaround traits. */
public record DriverDecisionProfile(
        InformationAccess informationAccess,
        double navigationHorizonSeconds,
        double informationLatencySeconds,
        double noticeReceptionProbability,
        double reroutePatienceSeconds,
        double minimumExpectedSavingSeconds,
        double rerouteCooldownSeconds,
        double stallEscapeSeconds,
        UTurnPolicy uTurnPolicy) {

    public DriverDecisionProfile {
        if (informationAccess == null || uTurnPolicy == null) {
            throw new IllegalArgumentException("Information access and U-turn policy are required.");
        }
        requirePositive("Navigation horizon", navigationHorizonSeconds);
        requireNonNegative("Information latency", informationLatencySeconds);
        if (!Double.isFinite(noticeReceptionProbability)
                || noticeReceptionProbability < 0 || noticeReceptionProbability > 1) {
            throw new IllegalArgumentException("Notice reception probability must be between 0 and 1.");
        }
        requireNonNegative("Reroute patience", reroutePatienceSeconds);
        requireNonNegative("Minimum expected saving", minimumExpectedSavingSeconds);
        requireNonNegative("Reroute cooldown", rerouteCooldownSeconds);
        requireNonNegative("Stall escape", stallEscapeSeconds);
        if (stallEscapeSeconds < reroutePatienceSeconds) {
            throw new IllegalArgumentException("Stall escape cannot be shorter than reroute patience.");
        }
    }

    public static DriverDecisionProfile defaults() {
        return forType(VehicleType.CAR);
    }

    public static DriverDecisionProfile forType(VehicleType type) {
        if (type == null) throw new IllegalArgumentException("Vehicle type is required.");
        return switch (type) {
            case CAR -> new DriverDecisionProfile(
                    InformationAccess.CONNECTED_NAVIGATION,
                    240.0, 8.0, 0.95, 25.0, 20.0, 45.0, 75.0,
                    UTurnPolicy.ONLY_WHEN_CURRENT_ROUTE_BLOCKED);
            case MOTORCYCLE -> new DriverDecisionProfile(
                    InformationAccess.CONNECTED_NAVIGATION,
                    210.0, 6.0, 0.93, 18.0, 15.0, 35.0, 55.0,
                    UTurnPolicy.WHEN_MATERIALLY_BETTER);
            case BUS -> new DriverDecisionProfile(
                    InformationAccess.BROADCAST,
                    300.0, 12.0, 0.98, 40.0, 35.0, 70.0, 120.0,
                    UTurnPolicy.ONLY_WHEN_CURRENT_ROUTE_BLOCKED);
            case EMERGENCY -> new DriverDecisionProfile(
                    InformationAccess.CONNECTED_NAVIGATION,
                    420.0, 1.0, 1.0, 3.0, 3.0, 3.0, 8.0,
                    UTurnPolicy.WHEN_MATERIALLY_BETTER);
        };
    }

    /** Stable bounded per-driver variation, independent of vehicle-list order. */
    public DriverDecisionProfile variedFor(String vehicleId) {
        return variedFor(vehicleId, 0L);
    }

    /** Stable bounded variation keyed by scenario seed and vehicle id. */
    public DriverDecisionProfile variedFor(String vehicleId, long scenarioSeed) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("Vehicle id is required.");
        }
        return new DriverDecisionProfile(
                informationAccess,
                navigationHorizonSeconds * factor(vehicleId, scenarioSeed, 21),
                informationLatencySeconds * factor(vehicleId, scenarioSeed, 22),
                clamp01(noticeReceptionProbability
                        * factor(vehicleId, scenarioSeed, 23)),
                reroutePatienceSeconds * factor(vehicleId, scenarioSeed, 24),
                minimumExpectedSavingSeconds * factor(vehicleId, scenarioSeed, 25),
                rerouteCooldownSeconds * factor(vehicleId, scenarioSeed, 26),
                Math.max(
                        reroutePatienceSeconds * factor(vehicleId, scenarioSeed, 24),
                        stallEscapeSeconds * factor(vehicleId, scenarioSeed, 27)),
                uTurnPolicy);
    }

    private static double factor(String id, long scenarioSeed, int salt) {
        long mixed = id.hashCode() * 0x9E3779B97F4A7C15L
                + salt * 0x632BE59BD9B4E019L;
        mixed ^= scenarioSeed * 0xD6E8FEB86659FD93L;
        mixed ^= mixed >>> 33;
        mixed *= 0xFF51AFD7ED558CCDL;
        mixed ^= mixed >>> 33;
        double unit = (mixed >>> 11) * 0x1.0p-53;
        return 0.85 + unit * 0.30;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
