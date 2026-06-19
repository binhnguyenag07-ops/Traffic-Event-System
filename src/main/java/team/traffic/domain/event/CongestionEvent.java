package team.traffic.domain.event;

import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;

public class CongestionEvent extends TrafficEvent {
    private final double congestionIncrease;

    public CongestionEvent(String id, String affectedRoadId, long startTick, long durationTicks, double congestionIncrease) {
        super(id, affectedRoadId, startTick, durationTicks);
        if (congestionIncrease < 0) {
            throw new IllegalArgumentException("Congestion increase must not be negative.");
        }
        this.congestionIncrease = congestionIncrease;
    }

    @Override
    public TrafficEventType getType() {
        return TrafficEventType.CONGESTION;
    }

    @Override
    public void apply(RoadNetwork roadNetwork) {
        super.apply(roadNetwork);
        rememberCurrentRoadCondition(roadNetwork);
        RoadCondition congestedCondition = copyCurrentRoadCondition(roadNetwork);
        congestedCondition.increaseCongestion(congestionIncrease);
        roadNetwork.updateRoadCondition(getAffectedRoadId(), congestedCondition);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        restorePreviousRoadCondition(roadNetwork);
        super.resolve(roadNetwork);
    }

    public double getCongestionIncrease() {
        return congestionIncrease;
    }
}
