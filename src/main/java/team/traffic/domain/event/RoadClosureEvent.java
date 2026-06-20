package team.traffic.domain.event;

import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

public class RoadClosureEvent extends TrafficEvent {
    public RoadClosureEvent(String id, String affectedRoadId, long startTick, long durationTicks) {
        super(id, affectedRoadId, startTick, durationTicks);
    }

    @Override
    public TrafficEventType getType() {
        return TrafficEventType.ROAD_CLOSURE;
    }

    @Override
    public void apply(RoadNetwork roadNetwork) {
        rememberCurrentRoadCondition(roadNetwork);
        RoadCondition closedCondition = copyCurrentRoadCondition(roadNetwork);
        closedCondition.setCongestionLevel(1.0);
        closedCondition.setRoadStatus(RoadStatus.CLOSED);
        roadNetwork.updateRoadCondition(getAffectedRoadId(), closedCondition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        restorePreviousRoadCondition(roadNetwork);
        super.resolve(roadNetwork);
    }
}
