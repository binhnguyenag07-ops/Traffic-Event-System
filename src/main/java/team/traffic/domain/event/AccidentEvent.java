package team.traffic.domain.event;

import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

public class AccidentEvent extends TrafficEvent {
    private final double capacityReduction;
    private final String causingVehicleId;

    public AccidentEvent(
            String id,
            String affectedRoadId,
            long startTick,
            long durationTicks,
            double capacityReduction,
            String causingVehicleId) {
        super(id, affectedRoadId, startTick, durationTicks);
        if (capacityReduction < 0 || capacityReduction > 1) {
            throw new IllegalArgumentException("Capacity reduction must be between 0 and 1.");
        }
        this.capacityReduction = capacityReduction;
        this.causingVehicleId = causingVehicleId;
    }

    @Override
    public TrafficEventType getType() {
        return TrafficEventType.ACCIDENT;
    }

    @Override
    public void apply(RoadNetwork roadNetwork) {
        RoadCondition accidentCondition = new RoadCondition(capacityReduction, 1.0, RoadStatus.BLOCKED);
        accidentCondition.markBlocked();
        roadNetwork.updateRoadCondition(getAffectedRoadId(), accidentCondition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        RoadCondition openCondition = new RoadCondition(0.0, 0.0, RoadStatus.OPEN);
        openCondition.markOpen();
        roadNetwork.updateRoadCondition(getAffectedRoadId(), openCondition);
        super.resolve(roadNetwork);
    }

    public double getCapacityReduction() {
        return capacityReduction;
    }

    public String getCausingVehicleId() {
        return causingVehicleId;
    }
}
