package team.traffic.domain.vehicle.dynamics;

/** Inputs required for a deterministic lane-change decision. */
public record LaneChangeRequest(
        TrafficParticipant vehicle,
        RoadOccupancy occupancy,
        double desiredSpeedMps,
        double accelerationMps2,
        double decelerationMps2,
        double timeStepSeconds,
        LaneChangeIntent intent) {

    public LaneChangeRequest(
            TrafficParticipant vehicle,
            RoadOccupancy occupancy,
            double desiredSpeedMps,
            double accelerationMps2,
            double decelerationMps2,
            double timeStepSeconds) {
        this(vehicle, occupancy, desiredSpeedMps, accelerationMps2,
                decelerationMps2, timeStepSeconds, LaneChangeIntent.DISCRETIONARY);
    }

    public LaneChangeRequest {
        if (vehicle == null || occupancy == null) throw new IllegalArgumentException("Vehicle and occupancy are required.");
        if (intent == null) throw new IllegalArgumentException("Lane-change intent is required.");
        if (!Double.isFinite(desiredSpeedMps) || desiredSpeedMps < 0
                || !Double.isFinite(accelerationMps2) || accelerationMps2 <= 0
                || !Double.isFinite(decelerationMps2) || decelerationMps2 <= 0
                || !Double.isFinite(timeStepSeconds) || timeStepSeconds <= 0) {
            throw new IllegalArgumentException("Lane-change dynamics must be finite and positive.");
        }
    }
}
