package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;

/** Read-only lane geometry required by microscopic vehicle dynamics. */
public interface RoadLayout {
    int laneCountOf(Road road);

    double laneWidthMetresOf(Road road);

    default double roadWidthMetresOf(Road road) {
        return laneCountOf(road) * laneWidthMetresOf(road);
    }
}
