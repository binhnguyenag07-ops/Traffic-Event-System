package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;


/**
 * One vehicle asking to cross one junction, together with everything the
 * right-of-way rules need to rank it against the others waiting there.
 *
 * @param vehicle                  the vehicle waiting to proceed
 * @param junctionId               the intersection being crossed
 * @param fromRoad                 the road the vehicle arrived on, or
 *                                 {@code null} when it is starting its trip here
 * @param toRoad                   the road it wants to enter
 * @param fromLaneIndex            lane held on the approach, zero for a departure
 * @param turnDirection            the manoeuvre it is about to perform
 * @param distanceToJunctionMetres how far its front bumper still is from the
 *                                 stop line
 * @param queueWaitSeconds         seconds since this vehicle last made
 *                                 meaningful longitudinal progress while on
 *                                 this approach. Unlike trip-wide delay, this
 *                                 resets between approaches and while a queue
 *                                 advances.
 */
public record JunctionApproach(
        TrafficParticipant vehicle,
        String junctionId,
        Road fromRoad,
        Road toRoad,
        int fromLaneIndex,
        TurnDirection turnDirection,
        double distanceToJunctionMetres,
        double queueWaitSeconds) {

    /** Compatibility constructor for callers whose participant is still on its approach road. */
    public JunctionApproach(
            TrafficParticipant vehicle,
            String junctionId,
            Road fromRoad,
            Road toRoad,
            TurnDirection turnDirection,
            double distanceToJunctionMetres,
            double queueWaitSeconds) {
        this(vehicle, junctionId, fromRoad, toRoad,
                vehicle == null ? 0 : vehicle.getLaneIndex(),
                turnDirection, distanceToJunctionMetres, queueWaitSeconds);
    }

    public JunctionApproach {
        if (vehicle == null) {
            throw new IllegalArgumentException("TrafficParticipant is required.");
        }
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id is required.");
        }
        if (toRoad == null) {
            throw new IllegalArgumentException("Target road is required.");
        }
        if (fromLaneIndex < 0) {
            throw new IllegalArgumentException("Approach lane index must not be negative.");
        }
        if (turnDirection == null) {
            throw new IllegalArgumentException("Turn direction is required.");
        }
        if (!Double.isFinite(distanceToJunctionMetres) || distanceToJunctionMetres < 0) {
            throw new IllegalArgumentException("Distance to junction must not be negative.");
        }
        if (!Double.isFinite(queueWaitSeconds) || queueWaitSeconds < 0) {
            throw new IllegalArgumentException("Queue wait must be finite and not negative.");
        }
    }

    public String vehicleId() {
        return vehicle.getId();
    }

    /** True when the vehicle is entering the network rather than crossing it. */
    public boolean isDeparture() {
        return fromRoad == null;
    }
}
