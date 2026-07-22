package team.traffic.domain.vehicle.dynamics;

/**
 * Safe lane-filtering approximation for a motorcycle in the discrete-lane
 * model. Its compact body and driver profile determine whether it fits; the
 * zero incentive threshold lets it use any adjacent lane with a genuine speed
 * benefit. Follower braking safety remains identical to ordinary MOBIL.
 *
 * <p>Continuous travel between lane centre lines is intentionally deferred to
 * the physical renderer and a future sub-lane occupancy model.</p>
 */
public final class MotorcycleLaneChangeModel implements LaneChangeModel {
    private final MobilLaneChangeModel delegate = new MobilLaneChangeModel(0.0);

    @Override
    public int chooseTargetLane(LaneChangeRequest request) {
        return delegate.chooseTargetLane(request);
    }

    @Override
    public String getName() {
        return "MOTORCYCLE_FILTERING_MOBIL";
    }
}
