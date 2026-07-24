package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * The curved path a vehicle follows across a junction, from an incoming lane to
 * an outgoing one.
 *
 * <p>Lane centrelines stop at the stop line and start again on the far side of
 * the junction; nothing joins them, so a vehicle drawn strictly on its lane
 * would jump the gap. This is the arc that fills it, curving from one lane's end
 * into the other's start so a turning vehicle sweeps round rather than cutting
 * the corner.</p>
 *
 * @param fromRoadId the road being left
 * @param fromLane   the lane on it
 * @param toRoadId   the road being joined
 * @param toLane     the lane on it
 * @param curve      the path between them, at least two points
 */
public record LaneConnector(
        String fromRoadId,
        int fromLane,
        String toRoadId,
        int toLane,
        List<GeoPoint> curve) {

    public LaneConnector {
        if (fromRoadId == null || fromRoadId.isBlank() || toRoadId == null || toRoadId.isBlank()) {
            throw new IllegalArgumentException("Both road ids are required.");
        }
        if (fromLane < 0 || toLane < 0) {
            throw new IllegalArgumentException("Lane indexes must not be negative.");
        }
        if (curve == null || curve.size() < 2) {
            throw new IllegalArgumentException("A connector needs at least two points.");
        }
        curve = List.copyOf(curve);
    }
}
