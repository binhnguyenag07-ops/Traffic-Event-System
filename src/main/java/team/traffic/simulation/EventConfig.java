package team.traffic.simulation;

public record EventConfig(
        String id,
        String type,
        String affectedRoadId,
        long startTick,
        long durationTicks,
        Double value,
        String causingVehicleId) {

    /**
     * Backward-compatible constructor without value and causingVehicleId.
     */
    public EventConfig(String id, String type, String affectedRoadId, long startTick, long durationTicks) {
        this(id, type, affectedRoadId, startTick, durationTicks, null, null);
    }
}
