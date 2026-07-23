package team.traffic.domain.vehicle.dynamics;

/** Chooses where to sit across the road during the planning half of a sub-step. */
public interface LaneChangeModel {
    int chooseTargetLane(LaneChangeRequest request);

    String getName();

    /**
     * Where to steer towards, as a position rather than a lane.
     *
     * <p>Models that think in lanes need not implement this: the default turns
     * their chosen lane into that lane's centre, which is exactly what they
     * meant. Only a model that can place a vehicle between lanes has to
     * override it.</p>
     */
    default LateralTarget chooseTarget(LaneChangeRequest request) {
        return LateralTarget.ofLane(chooseTargetLane(request), request.occupancy());
    }
}
