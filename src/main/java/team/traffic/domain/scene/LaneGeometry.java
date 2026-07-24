package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * The drawable centreline of one lane of one directed road.
 *
 * <p>The macro map draws a road as a single line down its middle. A lane-level
 * renderer needs each lane placed where its traffic actually runs, half a lane
 * either side of that line on a two-lane road, and it needs the line to bend
 * with the road rather than jump between its shape points. This is that line,
 * already offset into position, ready to be drawn or to have vehicles placed on
 * it.</p>
 *
 * <p>The geometry is derived, never stored: it is rebuilt from the road's own
 * shape and its lane layout, so it cannot disagree with the model the way a
 * second copy would. Coordinates stay geographic here; converting to local
 * metres is the API boundary's job, done once, so the renderer never mixes the
 * two.</p>
 *
 * @param roadId       the directed road this lane belongs to
 * @param laneIndex    zero-based, from the side lane offsets are measured from
 * @param widthMetres  how wide to draw the lane
 * @param centreline   the lane's own centreline, at least two points
 * @param oneway       whether the road carries traffic in one direction only
 */
public record LaneGeometry(
        String roadId,
        int laneIndex,
        double widthMetres,
        List<GeoPoint> centreline,
        boolean oneway) {

    public LaneGeometry {
        if (roadId == null || roadId.isBlank()) {
            throw new IllegalArgumentException("Road id must not be blank.");
        }
        if (laneIndex < 0) {
            throw new IllegalArgumentException("Lane index must not be negative.");
        }
        if (!Double.isFinite(widthMetres) || widthMetres <= 0) {
            throw new IllegalArgumentException("Lane width must be positive.");
        }
        if (centreline == null || centreline.size() < 2) {
            throw new IllegalArgumentException("A lane centreline needs at least two points.");
        }
        centreline = List.copyOf(centreline);
    }
}
