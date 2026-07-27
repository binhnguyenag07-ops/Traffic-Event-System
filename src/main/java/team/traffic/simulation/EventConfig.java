package team.traffic.simulation;

import team.traffic.domain.event.EventOrigin;
import team.traffic.domain.event.InformationPolicy;

public record EventConfig(
        String id,
        String type,
        String affectedRoadId,
        double startTimeSeconds,
        double durationSeconds,
        Double value,
        String causingVehicleId,
        Double positionAlongRoad,
        java.util.List<Integer> blockedLaneIndexes,
        EventOrigin origin,
        InformationPolicy informationPolicy) {

    public EventConfig {
        blockedLaneIndexes = blockedLaneIndexes == null ? java.util.List.of() : java.util.List.copyOf(blockedLaneIndexes);
        origin = origin == null ? EventOrigin.SCENARIO : origin;
        informationPolicy = informationPolicy == null
                ? InformationPolicy.broadcastOnActivation()
                : informationPolicy;
    }

    public EventConfig(
            String id,
            String type,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            Double value,
            String causingVehicleId,
            Double positionAlongRoad,
            java.util.List<Integer> blockedLaneIndexes) {
        this(id, type, affectedRoadId, startTimeSeconds, durationSeconds,
                value, causingVehicleId, positionAlongRoad, blockedLaneIndexes,
                EventOrigin.SCENARIO, InformationPolicy.broadcastOnActivation());
    }

    public EventConfig(String id, String type, String affectedRoadId,
            double startTimeSeconds, double durationSeconds, Double value, String causingVehicleId) {
        this(id, type, affectedRoadId, startTimeSeconds, durationSeconds,
                value, causingVehicleId, null, java.util.List.of());
    }

    /**
     * Backward-compatible constructor without value and causingVehicleId.
     */
    public EventConfig(String id, String type, String affectedRoadId,
            double startTimeSeconds, double durationSeconds) {
        this(id, type, affectedRoadId, startTimeSeconds, durationSeconds, null, null);
    }

    /** @deprecated use {@link #startTimeSeconds()}. */
    @Deprecated
    public long startTick() {
        return Math.round(startTimeSeconds);
    }

    /** @deprecated use {@link #durationSeconds()}. */
    @Deprecated
    public long durationTicks() {
        return Math.round(durationSeconds);
    }
}
