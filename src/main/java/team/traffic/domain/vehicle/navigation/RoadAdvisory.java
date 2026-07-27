package team.traffic.domain.vehicle.navigation;

import java.util.OptionalDouble;

/** One event or congestion notice that has reached a particular driver. */
public record RoadAdvisory(
        String advisoryId,
        String roadId,
        int routeStepOffset,
        double distanceAheadMetres,
        double expectedEntryAtSeconds,
        AdvisoryKind kind,
        double expectedDelaySeconds,
        double confidence,
        InformationSource source,
        double publishedAtSeconds,
        double receivedAtSeconds,
        OptionalDouble validUntilSeconds) {

    public RoadAdvisory {
        if (advisoryId == null || advisoryId.isBlank() || roadId == null || roadId.isBlank()
                || routeStepOffset < 0 || kind == null || source == null) {
            throw new IllegalArgumentException("Advisory identity, route position, kind, and source are required.");
        }
        requireNonNegative("Distance ahead", distanceAheadMetres);
        requireNonNegative("Expected entry", expectedEntryAtSeconds);
        requireNonNegative("Expected delay", expectedDelaySeconds);
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("Advisory confidence must be between 0 and 1.");
        }
        requireNonNegative("Published time", publishedAtSeconds);
        requireNonNegative("Received time", receivedAtSeconds);
        if (receivedAtSeconds < publishedAtSeconds) {
            throw new IllegalArgumentException("An advisory cannot arrive before publication.");
        }
        validUntilSeconds = validUntilSeconds == null ? OptionalDouble.empty() : validUntilSeconds;
        if (validUntilSeconds.isPresent()
                && validUntilSeconds.getAsDouble() < receivedAtSeconds) {
            throw new IllegalArgumentException("Advisory validity cannot end before reception.");
        }
    }

    public boolean isValidAt(double simulationSeconds) {
        return receivedAtSeconds <= simulationSeconds
                && (validUntilSeconds.isEmpty()
                        || simulationSeconds < validUntilSeconds.getAsDouble());
    }

    private static void requireNonNegative(String name, double value) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative.");
        }
    }
}
