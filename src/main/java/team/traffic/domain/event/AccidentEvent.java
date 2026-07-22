package team.traffic.domain.event;

import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import java.util.Set;

public class AccidentEvent extends TrafficEvent {
    private final double capacityReduction;
    private final String causingVehicleId;
    private final Set<String> involvedVehicleIds;

    public AccidentEvent(
            String id,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            double capacityReduction,
            String causingVehicleId) {
        this(id, affectedRoadId, startTimeSeconds, durationSeconds, capacityReduction,
                causingVehicleId, 0.5, Set.of(), Set.of());
    }

    public AccidentEvent(
            String id,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            double capacityReduction,
            String causingVehicleId,
            double positionAlongRoad,
            Set<Integer> blockedLaneIndexes) {
        this(id, affectedRoadId, startTimeSeconds, durationSeconds, capacityReduction,
                causingVehicleId, positionAlongRoad, blockedLaneIndexes, Set.of());
    }

    public AccidentEvent(
            String id,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            double capacityReduction,
            String causingVehicleId,
            double positionAlongRoad,
            Set<Integer> blockedLaneIndexes,
            Set<String> involvedVehicleIds) {
        super(id, affectedRoadId, startTimeSeconds, durationSeconds,
                positionAlongRoad, blockedLaneIndexes);
        if (capacityReduction < 0 || capacityReduction > 1) {
            throw new IllegalArgumentException("Capacity reduction must be between 0 and 1.");
        }
        if (causingVehicleId == null || causingVehicleId.isBlank()) {
            throw new IllegalArgumentException("Causing vehicle id must not be blank.");
        }
        this.capacityReduction = capacityReduction;
        this.causingVehicleId = causingVehicleId;
        java.util.LinkedHashSet<String> involved = new java.util.LinkedHashSet<>();
        if (involvedVehicleIds != null) {
            involvedVehicleIds.stream()
                    .filter(java.util.Objects::nonNull)
                    .filter(vehicleId -> !vehicleId.isBlank())
                    .forEach(involved::add);
        }
        involved.add(causingVehicleId);
        this.involvedVehicleIds = Set.copyOf(involved);
    }

    @Override
    public TrafficEventType getType() {
        return TrafficEventType.ACCIDENT;
    }

    @Override
    public void apply(RoadNetwork roadNetwork) {
        rememberCurrentRoadCondition(roadNetwork);
        RoadCondition accidentCondition = copyCurrentRoadCondition(roadNetwork);
        accidentCondition.setVehicleDensity(Math.max(accidentCondition.getVehicleDensity(), capacityReduction));
        if (getBlockedLaneIndexes().isEmpty()) {
            accidentCondition.markBlocked();
        } else {
            accidentCondition.increaseCongestion(capacityReduction);
        }
        roadNetwork.updateRoadCondition(getAffectedRoadId(), accidentCondition);
        super.apply(roadNetwork);
    }

    @Override
    public void resolve(RoadNetwork roadNetwork) {
        restorePreviousRoadCondition(roadNetwork);
        super.resolve(roadNetwork);
    }

    public double getCapacityReduction() {
        return capacityReduction;
    }

    public String getCausingVehicleId() {
        return causingVehicleId;
    }

    public Set<String> getInvolvedVehicleIds() {
        return involvedVehicleIds;
    }
}
