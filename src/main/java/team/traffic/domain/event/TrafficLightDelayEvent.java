package team.traffic.domain.event;

public class TrafficLightDelayEvent extends TrafficEvent {
    private long delayTicks;

    public TrafficLightDelayEvent(String id, String affectedRoadId, long startTick, long durationTicks, long delayTicks) {
        super(id, affectedRoadId, startTick, durationTicks);
        this.delayTicks = delayTicks;
    }

    @Override
    public String getType() {
        return TrafficEventType.TRAFFIC_LIGHT_DELAY;
    }

    @Override
    public void apply() {
        // TODO: add delay to vehicles waiting at the affected road/intersection.
        throw new UnsupportedOperationException("TODO: implement traffic light delay apply.");
    }

    @Override
    public void resolve() {
        // TODO: remove delay after event duration.
        throw new UnsupportedOperationException("TODO: implement traffic light delay resolve.");
    }

    public long getDelayTicks() {
        return delayTicks;
    }

    public void setDelayTicks(long delayTicks) {
        this.delayTicks = delayTicks;
    }
}
