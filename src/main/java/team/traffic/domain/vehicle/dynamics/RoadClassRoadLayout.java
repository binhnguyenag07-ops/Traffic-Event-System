package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;

/**
 * Layout fallback for maps that predate explicit lane data. This class is also
 * useful when auditing imported maps because it ignores any stored override.
 */
public final class RoadClassRoadLayout implements RoadLayout {
    @Override
    public int laneCountOf(Road road) {
        return roadClassOf(road).getDefaultLaneCount();
    }

    @Override
    public double laneWidthMetresOf(Road road) {
        return roadClassOf(road).getDefaultLaneWidthMetres();
    }

    private static RoadClass roadClassOf(Road road) {
        if (road == null) {
            throw new IllegalArgumentException("Road is required.");
        }
        return road.getRoadClass() == null ? RoadClass.UNCLASSIFIED : road.getRoadClass();
    }
}
