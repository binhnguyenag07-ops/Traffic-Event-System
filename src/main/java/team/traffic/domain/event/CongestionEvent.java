package team.traffic.domain.event;

public class CongestionEvent extends TrafficEvent {
    private double congestionIncrease;

    public CongestionEvent(String id, String affectedRoadId, long startTick, long durationTicks, double congestionIncrease) {
        super(id, affectedRoadId, startTick, durationTicks);
        this.congestionIncrease = congestionIncrease;
    }

    @Override
    public String getType() {
        return TrafficEventType.CONGESTION;
    }

    @Override
    public void apply() {
        // TODO: increase RoadCondition.congestionLevel on affected road.
        throw new UnsupportedOperationException("TODO: implement congestion event apply.");
    }

    @Override
    public void resolve() {
        // TODO: rollback or recalculate congestion on affected road.
        throw new UnsupportedOperationException("TODO: implement congestion event resolve.");
    }

    public double getCongestionIncrease() {
        return congestionIncrease;
    }

    public void setCongestionIncrease(double congestionIncrease) {
        this.congestionIncrease = congestionIncrease;
    }
}
