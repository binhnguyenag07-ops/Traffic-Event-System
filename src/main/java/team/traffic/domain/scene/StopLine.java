package team.traffic.domain.scene;

import team.traffic.domain.network.GeoPoint;

/**
 * The line a vehicle stops at when its approach to a junction is red.
 *
 * <p>Drawn across the full width of the road it belongs to, just short of the
 * junction so that a stopped queue does not sit in the middle of it. The two
 * endpoints span the carriageway; a renderer strokes between them.</p>
 *
 * @param roadId the directed road whose exit this line guards
 * @param from   one edge of the carriageway
 * @param to     the other edge
 */
public record StopLine(String roadId, GeoPoint from, GeoPoint to) {

    public StopLine {
        if (roadId == null || roadId.isBlank()) {
            throw new IllegalArgumentException("Road id must not be blank.");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("A stop line needs both endpoints.");
        }
    }
}
