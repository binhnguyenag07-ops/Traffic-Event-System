package team.traffic.domain.demand;

import java.util.EnumMap;
import java.util.Map;
import team.traffic.domain.network.BuildingType;

public record DemandParameters(
        Map<BuildingType, BuildingDemandParameters> buildingTypes,
        double distanceDecayBeta) {

    public DemandParameters {
        if (buildingTypes == null) throw new IllegalArgumentException("Building demand parameters are required");
        EnumMap<BuildingType, BuildingDemandParameters> copy = new EnumMap<>(BuildingType.class);
        copy.putAll(buildingTypes);
        for (BuildingType type : BuildingType.values()) {
            if (!copy.containsKey(type)) {
                throw new IllegalArgumentException("Missing demand parameters for building type " + type);
            }
        }
        buildingTypes = Map.copyOf(copy);
        if (!Double.isFinite(distanceDecayBeta) || distanceDecayBeta < 0) {
            throw new IllegalArgumentException("Distance decay beta must be finite and non-negative");
        }
    }

    public BuildingDemandParameters forType(BuildingType type) {
        return buildingTypes.get(type);
    }
}
