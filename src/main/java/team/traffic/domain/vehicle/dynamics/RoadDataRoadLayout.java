package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;

/** Uses lane data restored on {@link Road}. */
public final class RoadDataRoadLayout implements RoadLayout {
    @Override
    public int laneCountOf(Road road) {
        requireRoad(road);
        return road.getLaneCount();
    }

    @Override
    public double laneWidthMetresOf(Road road) {
        requireRoad(road);
        return road.getLaneWidthMetres();
    }

    private static void requireRoad(Road road) {
        if (road == null) {
            throw new IllegalArgumentException("Road is required.");
        }
    }
}
