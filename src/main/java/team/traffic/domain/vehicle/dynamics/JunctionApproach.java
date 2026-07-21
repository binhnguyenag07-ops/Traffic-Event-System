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
 * @param turnDirection            the manoeuvre it is about to perform
 * @param distanceToJunctionMetres how far its front bumper still is from the
 *                                 stop line
 * @param waitedTicks              how long it has already been held up, used so
 *                                 a vehicle on a minor road is not starved
 */
public record JunctionApproach(
        TrafficParticipant vehicle,
        String junctionId,
        Road fromRoad,
        Road toRoad,
        TurnDirection turnDirection,
        double distanceToJunctionMetres,
        long waitedTicks) {

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
        if (turnDirection == null) {
            throw new IllegalArgumentException("Turn direction is required.");
        }
        if (!Double.isFinite(distanceToJunctionMetres) || distanceToJunctionMetres < 0) {
            throw new IllegalArgumentException("Distance to junction must not be negative.");
        }
        if (waitedTicks < 0) {
            throw new IllegalArgumentException("Waited ticks must not be negative.");
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
