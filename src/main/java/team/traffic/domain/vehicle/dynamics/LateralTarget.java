package team.traffic.domain.vehicle.dynamics;

/**
 * Where a vehicle has decided to place itself across the road.
 *
 * <p>A lane index alone cannot express the decision a filtering rider makes.
 * Riding the line between lanes one and two is not lane one and not lane two,
 * and rounding it to either loses the only thing that made it worth doing. The
 * offset is therefore the real decision and the lane index is what the rest of
 * the system is told, so snapshots, statistics, and anything still counting
 * vehicles per lane keep working.</p>
 *
 * @param laneIndex    lane the vehicle is nominally in, nearest to the offset
 * @param offsetMetres signed distance from the road centreline
 */
public record LateralTarget(int laneIndex, double offsetMetres) {

    public LateralTarget {
        if (laneIndex < 0) {
            throw new IllegalArgumentException("Target lane index must not be negative.");
        }
        if (!Double.isFinite(offsetMetres)) {
            throw new IllegalArgumentException("Target offset must be finite.");
        }
    }

    /** A target that sits squarely on the centre of one lane. */
    public static LateralTarget ofLane(int laneIndex, RoadOccupancy occupancy) {
        if (occupancy == null) {
            throw new IllegalArgumentException("Occupancy is required.");
        }
        return new LateralTarget(laneIndex, occupancy.laneCentreOffsetMetres(laneIndex));
    }

    /** A free offset, labelled with whichever lane it is closest to. */
    public static LateralTarget ofOffset(double offsetMetres, RoadOccupancy occupancy) {
        if (occupancy == null) {
            throw new IllegalArgumentException("Occupancy is required.");
        }
        return new LateralTarget(nearestLaneIndex(offsetMetres, occupancy), offsetMetres);
    }

    /** Whether this target is a lane centre rather than a gap between lanes. */
    public boolean isLaneCentre(RoadOccupancy occupancy) {
        return LateralMotionModel.hasSettled(
                offsetMetres, occupancy.laneCentreOffsetMetres(laneIndex));
    }

    private static int nearestLaneIndex(double offsetMetres, RoadOccupancy occupancy) {
        int nearest = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index < occupancy.getLaneCount(); index++) {
            double distance = Math.abs(offsetMetres - occupancy.laneCentreOffsetMetres(index));
            if (distance < best) {
                best = distance;
                nearest = index;
            }
        }
        return nearest;
    }
}
