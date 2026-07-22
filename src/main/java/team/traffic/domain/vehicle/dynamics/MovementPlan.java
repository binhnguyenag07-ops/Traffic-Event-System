package team.traffic.domain.vehicle.dynamics;

/**
 * What one participant intends to do during the coming step, decided but not
 * yet carried out.
 *
 * <p>Deciding and moving are two separate phases so that every participant
 * plans against the same unmoved picture of the road. Passing the decision
 * around as a value rather than parking it in fields on the participant makes
 * that ordering explicit: there is nothing to commit until a plan has been
 * handed over, and a plan cannot be quietly overwritten before it is used.</p>
 *
 * @param roadId        road the movement takes place on
 * @param speedMps      speed chosen for the step, in metres per second
 * @param travelMetres  distance to cover, already capped at the road's exit
 * @param mayLeaveRoad  whether running off the end is permitted, which it is
 *                      not when the road beyond has no room
 * @param targetLaneIndex lane occupied during this movement
 */
public record MovementPlan(
        String roadId,
        double speedMps,
        double travelMetres,
        boolean mayLeaveRoad,
        int targetLaneIndex) {

    public MovementPlan(String roadId, double speedMps, double travelMetres, boolean mayLeaveRoad) {
        this(roadId, speedMps, travelMetres, mayLeaveRoad, 0);
    }

    public MovementPlan {
        if (roadId == null || roadId.isBlank()) {
            throw new IllegalArgumentException("Planned road id is required.");
        }
        if (!Double.isFinite(speedMps) || speedMps < 0) {
            throw new IllegalArgumentException("Planned speed must not be negative.");
        }
        if (!Double.isFinite(travelMetres) || travelMetres < 0) {
            throw new IllegalArgumentException("Planned travel distance must not be negative.");
        }
        if (targetLaneIndex < 0) {
            throw new IllegalArgumentException("Target lane index must not be negative.");
        }
    }
}
