package team.traffic.domain.event;

import java.util.Set;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;

/** A scripted vehicle failure that leaves a stationary lane obstruction. */
public final class BreakdownEvent extends TrafficEvent {
    private final String affectedVehicleId;

    public BreakdownEvent(
            String id,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            String affectedVehicleId,
            double positionAlongRoad,
            Set<Integer> blockedLaneIndexes) {
        super(id, affectedRoadId, startTimeSeconds, durationSeconds,
                positionAlongRoad, blockedLaneIndexes);
        if (affectedVehicleId == null || affectedVehicleId.isBlank()) {
            throw new IllegalArgumentException("Affected vehicle id is required.");
        }
        this.affectedVehicleId = affectedVehicleId;
    }

    @Override
    public TrafficEventType getType() {
        return TrafficEventType.BREAKDOWN;
    }

    @Override
    public void apply(RoadNetwork roadNetwork) {
        rememberCurrentRoadCondition(roadNetwork);
        RoadCondition condition = copyCurrentRoadCondition(roadNetwork);
        condition.increaseCongestion(0.2);
        roadNetwork.updateRoadCondition(getAffectedRoadId(), condition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        restorePreviousRoadCondition(roadNetwork);
        super.resolve(roadNetwork);
    }

    public String getAffectedVehicleId() {
        return affectedVehicleId;
    }
}
