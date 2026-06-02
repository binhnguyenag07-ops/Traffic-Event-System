package team.traffic.domain.event;

public class AccidentEvent extends TrafficEvent {
    private double capacityReduction; // e.g. 0.5 means 50% capacity reduction
    private String causingVehicleId;

    public AccidentEvent(String id, String affectedRoadId, long startTick, long durationTicks, double capacityReduction, String causingVehicleId) {
        super(id, affectedRoadId, startTick, durationTicks);
        this.capacityReduction = capacityReduction;
        this.causingVehicleId = causingVehicleId;
    }

    public double getCapacityReduction() { return capacityReduction; }
    public String getCausingVehicleId() { return causingVehicleId; }

    @Override
    public String getType() {
        return "ACCIDENT";
    }

    @Override
    public void apply() {
        // Logic will be implemented here to reduce the road's capacity by the reduction factor
        // and trigger congestion if current vehicles exceed the new capacity.
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void resolve() {
        // Restore road capacity
        throw new UnsupportedOperationException("TODO: implement");
    }
}
