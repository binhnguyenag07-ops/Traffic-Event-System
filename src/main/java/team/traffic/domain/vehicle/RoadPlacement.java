package team.traffic.domain.vehicle;

/**
 * Where a vehicle physically sits on the road network.
 *
 * <p>Seven values that are only ever meaningful together: the road being
 * occupied, the two junctions it runs between, how far along it the vehicle is,
 * and the lane and lateral offset it holds across it. A vehicle either has all
 * of them or none, and it acquires and discards them as one act.</p>
 *
 * <p>They used to be seven fields on {@code Vehicle}, cleared by hand in one
 * method and set by hand in another. That worked until a new one was added:
 * adding a lateral offset meant remembering three separate places to initialise
 * and clear it, and adding the lateral target right after meant remembering the
 * same three again. Missing one would have left a vehicle carrying part of its
 * old position onto its new road — a bug that would surface as a car mysteriously
 * starting a road already half way across it.</p>
 *
 * <p>Gathering them here makes that impossible to get wrong: {@link #release()}
 * clears the lot, and a value added later is cleared by the same line.</p>
 */
public final class RoadPlacement {
    private String occupiedRoadId;
    private String startIntersectionId;
    private String endIntersectionId;
    private double progress;
    private int laneIndex;
    private double lateralOffsetMetres;
    private double lateralTargetOffsetMetres;

    /** Whether the vehicle holds a road at all, as opposed to waiting at a junction. */
    public boolean isOnRoad() {
        return occupiedRoadId != null;
    }

    /**
     * Takes up a position at the start of a road, lined up with its lane.
     *
     * <p>The vehicle arrives already aligned: whatever sideways movement it
     * needed happened while crossing the junction, which this model does not
     * simulate in metres.</p>
     */
    public void enter(
            String roadId,
            String startIntersectionId,
            String endIntersectionId,
            int laneIndex,
            double laneCentreOffsetMetres) {
        if (roadId == null || roadId.isBlank()) {
            throw new IllegalArgumentException("Occupied road id must not be blank.");
        }
        if (laneIndex < 0) {
            throw new IllegalArgumentException("Lane index must not be negative.");
        }
        if (!Double.isFinite(laneCentreOffsetMetres)) {
            throw new IllegalArgumentException("Lane centre offset must be finite.");
        }
        this.occupiedRoadId = roadId;
        this.startIntersectionId = startIntersectionId;
        this.endIntersectionId = endIntersectionId;
        this.progress = 0.0;
        this.laneIndex = laneIndex;
        this.lateralOffsetMetres = laneCentreOffsetMetres;
        this.lateralTargetOffsetMetres = laneCentreOffsetMetres;
    }

    /** Gives up the road and every value that only meant something while on it. */
    public void release() {
        occupiedRoadId = null;
        startIntersectionId = null;
        endIntersectionId = null;
        progress = 0.0;
        laneIndex = 0;
        lateralOffsetMetres = 0.0;
        lateralTargetOffsetMetres = 0.0;
    }

    /** Records the outcome of one movement step. */
    public void moveTo(
            double progress, int laneIndex, double lateralOffsetMetres, double lateralTargetOffsetMetres) {
        if (!Double.isFinite(progress) || progress < 0 || progress > 1) {
            throw new IllegalArgumentException("Road progress must be between 0 and 1.");
        }
        if (laneIndex < 0) {
            throw new IllegalArgumentException("Lane index must not be negative.");
        }
        if (!Double.isFinite(lateralOffsetMetres) || !Double.isFinite(lateralTargetOffsetMetres)) {
            throw new IllegalArgumentException("Lateral position must be finite.");
        }
        this.progress = progress;
        this.laneIndex = laneIndex;
        this.lateralOffsetMetres = lateralOffsetMetres;
        this.lateralTargetOffsetMetres = lateralTargetOffsetMetres;
    }

    public String getOccupiedRoadId() {
        return occupiedRoadId;
    }

    public String getStartIntersectionId() {
        return startIntersectionId;
    }

    public String getEndIntersectionId() {
        return endIntersectionId;
    }

    public double getProgress() {
        return progress;
    }

    public int getLaneIndex() {
        return laneIndex;
    }

    public double getLateralOffsetMetres() {
        return lateralOffsetMetres;
    }

    /**
     * Where the vehicle is steering towards, which is not always a lane centre:
     * a filtering rider aims at the gap between two lanes.
     */
    public double getLateralTargetOffsetMetres() {
        return lateralTargetOffsetMetres;
    }
}
