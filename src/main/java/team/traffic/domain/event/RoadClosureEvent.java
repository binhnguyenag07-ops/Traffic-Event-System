package team.traffic.domain.event;

public class RoadClosureEvent extends TrafficEvent {
    public RoadClosureEvent(String id, String affectedRoadId, long startTick, long durationTicks) {
        super(id, affectedRoadId, startTick, durationTicks);
    }

    @Override
    public String getType() {
        return TrafficEventType.ROAD_CLOSURE;
    }

    @Override
    public void apply() {
        // TODO: set affected road status to CLOSED.
        throw new UnsupportedOperationException("TODO: implement road closure apply.");
    }

    @Override
    public void resolve() {
        // TODO: restore affected road status after closure ends.
        throw new UnsupportedOperationException("TODO: implement road closure resolve.");
    }
}
