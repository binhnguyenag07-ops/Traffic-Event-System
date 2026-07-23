package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;

/** Read-only lane geometry required by microscopic vehicle dynamics. */
public interface RoadLayout {
    int laneCountOf(Road road);

    double laneWidthMetresOf(Road road);

    default double roadWidthMetresOf(Road road) {
        return laneCountOf(road) * laneWidthMetresOf(road);
    }

    /** Signed offset of a lane's centre from the road centreline, in metres. */
    default double laneCentreOffsetMetresOf(Road road, int laneIndex) {
        return laneCentreOffsetMetres(laneIndex, laneCountOf(road), laneWidthMetresOf(road));
    }

    /**
     * Signed offset of a lane's centre from the road centreline, in metres.
     *
     * <p>Lanes are numbered from zero on one side, so the road centreline sits
     * halfway between the first and last lane centre. An odd lane count puts the
     * middle lane exactly on the centreline; an even count straddles it. Kept
     * here as the single definition because a pose resolver drawing pixels and
     * an occupancy model deciding lane changes must agree on where a lane
     * physically is.</p>
     */
    static double laneCentreOffsetMetres(int laneIndex, int laneCount, double laneWidthMetres) {
        if (laneCount <= 0) {
            throw new IllegalArgumentException("Lane count must be positive.");
        }
        return (laneIndex - (laneCount - 1) / 2.0) * laneWidthMetres;
    }
}
