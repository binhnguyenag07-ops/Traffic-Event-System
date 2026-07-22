package team.traffic.simulation.trace;

/** One macroscopic observation derived from microscopic vehicle state. */
public record RoadFlowSample(
        String roadId,
        long tick,
        double elapsedSeconds,
        int vehicleCount,
        double meanSpeedMps,
        double densityVehPerKmPerLane,
        double flowVehPerHourPerLane) {

    public RoadFlowSample {
        if (roadId == null || roadId.isBlank()) throw new IllegalArgumentException("Road id is required.");
        if (tick < 0 || elapsedSeconds < 0 || vehicleCount < 0) {
            throw new IllegalArgumentException("Flow sample time and count must not be negative.");
        }
        if (!Double.isFinite(meanSpeedMps) || meanSpeedMps < 0
                || !Double.isFinite(densityVehPerKmPerLane) || densityVehPerKmPerLane < 0
                || !Double.isFinite(flowVehPerHourPerLane) || flowVehPerHourPerLane < 0) {
            throw new IllegalArgumentException("Flow sample measurements must be finite and not negative.");
        }
    }
}
