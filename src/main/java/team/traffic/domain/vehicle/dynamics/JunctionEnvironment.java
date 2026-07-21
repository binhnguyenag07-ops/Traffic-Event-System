package team.traffic.domain.vehicle.dynamics;

/**
 * What a junction needs to know about the traffic around it in order to decide
 * whether a waiting vehicle may go.
 *
 * <p>Declared separately from {@link TrafficState} so the junction logic can be
 * unit tested against a hand-written stand-in rather than a whole network.</p>
 */
public interface JunctionEnvironment {

    /** Whether the road this movement leads onto has room for the vehicle. */
    boolean hasPhysicalSpaceFor(JunctionApproach approach);

    /**
     * Time until the next vehicle on a conflicting stream reaches the junction,
     * in seconds, ignoring the approach the waiting vehicle came from. Returns
     * {@link Double#POSITIVE_INFINITY} when nothing is coming.
     */
    double secondsUntilNextArrival(String junctionId, String excludedIncomingRoadId);
}
