package team.traffic.domain.event;

import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

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
        RoadCondition congestedCondition = new RoadCondition(0.0, 0.0, RoadStatus.OPEN);
        congestedCondition.increaseCongestion(congestionIncrease);
        roadNetwork.updateRoadCondition(getAffectedRoadId(), congestedCondition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        RoadCondition openCondition = new RoadCondition(0.0, 0.0, RoadStatus.OPEN);
        openCondition.markOpen();
        roadNetwork.updateRoadCondition(getAffectedRoadId(), openCondition);
        super.resolve(roadNetwork);
    }

    public double getCongestionIncrease() {
        return congestionIncrease;
    }
}
