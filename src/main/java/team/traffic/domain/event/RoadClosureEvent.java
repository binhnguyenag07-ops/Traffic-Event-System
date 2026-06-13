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
        RoadCondition closedCondition = new RoadCondition(1.0, 1.0, RoadStatus.CLOSED);
        closedCondition.markBlocked();
        roadNetwork.updateRoadCondition(getAffectedRoadId(), closedCondition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        RoadCondition openCondition = new RoadCondition(0.0, 0.0, RoadStatus.OPEN);
        openCondition.markOpen();
        roadNetwork.updateRoadCondition(getAffectedRoadId(), openCondition);
        super.resolve(roadNetwork);
    }
}
