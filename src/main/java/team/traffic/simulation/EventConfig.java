package team.traffic.simulation;

public record EventConfig(
        String id,
        String type,
        String affectedRoadId,
        double startTimeSeconds,
        double durationSeconds,
        Double value,
        String causingVehicleId,
        Double positionAlongRoad,
        java.util.List<Integer> blockedLaneIndexes) {

    public EventConfig {
        blockedLaneIndexes = blockedLaneIndexes == null ? java.util.List.of() : java.util.List.copyOf(blockedLaneIndexes);
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
