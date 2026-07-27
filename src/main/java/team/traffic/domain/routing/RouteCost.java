package team.traffic.domain.routing;

/** Comparable evidence used when deciding whether a new route is worthwhile. */
public record RouteCost(
        double travelTimeSeconds,
        double distanceKm,
        double manoeuvrePenaltySeconds,
        double uncertaintySeconds) {

    public RouteCost {
        requireNonNegative("Travel time", travelTimeSeconds);
        requireNonNegative("Distance", distanceKm);
        requireNonNegative("Manoeuvre penalty", manoeuvrePenaltySeconds);
        requireNonNegative("Uncertainty", uncertaintySeconds);
    }

    public double generalizedSeconds() {
        return travelTimeSeconds + manoeuvrePenaltySeconds + uncertaintySeconds;
    }

    public static RouteCost from(Route route) {
        if (route == null) {
            return new RouteCost(
                    Double.MAX_VALUE, Double.MAX_VALUE, 0.0, 0.0);
        }
        return new RouteCost(
                route.getEstimatedTravelTime() * 3_600.0,
                route.getTotalDistance(),
                0.0,
                0.0);
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
