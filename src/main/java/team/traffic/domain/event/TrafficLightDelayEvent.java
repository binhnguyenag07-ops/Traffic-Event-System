package team.traffic.domain.event;

import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;

public class TrafficLightDelayEvent extends TrafficEvent {
    private final long delayTicks;

    public TrafficLightDelayEvent(
            String id,
            String affectedRoadId,
            long startTick,
            long durationTicks,
            long delayTicks) {
        super(id, affectedRoadId, startTick, durationTicks);
        if (delayTicks < 0) {
            throw new IllegalArgumentException("Delay ticks must not be negative.");
        }
        this.delayTicks = delayTicks;
    }

    @Override
    public TrafficEventType getType() {
        return TrafficEventType.TRAFFIC_LIGHT_DELAY;
    }

    @Override
    public void apply(RoadNetwork roadNetwork) {
        rememberCurrentRoadCondition(roadNetwork);
        RoadCondition delayedCondition = copyCurrentRoadCondition(roadNetwork);
        delayedCondition.increaseCongestion(calculateDelayImpact());
        roadNetwork.updateRoadCondition(getAffectedRoadId(), delayedCondition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        restorePreviousRoadCondition(roadNetwork);
        super.resolve(roadNetwork);
    }

    public long getDelayTicks() {
        return delayTicks;
    }

    private double calculateDelayImpact() {
        if (getDurationTicks() == 0) {
            return delayTicks;
        }
        return (double) delayTicks / getDurationTicks();
    }
}
