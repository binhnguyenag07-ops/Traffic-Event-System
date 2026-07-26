package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.LandCoverType;

/**
 * A patch of ground the renderer fills, so the space between the roads says
 * what it is rather than being one flat colour.
 *
 * <p>Only mapped patches appear here. The ordinary fabric that fills everything
 * else is not a polygon at all — it is the background the scene is drawn on,
 * which is why {@link LandCoverType#NORMAL} needs no entry.</p>
 */
public record SceneLandCover(String id, LandCoverType type, List<GeoPoint> polygon) {

    public SceneLandCover {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Land cover id must not be blank.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Land cover type is required.");
        }
        polygon = polygon == null ? List.of() : List.copyOf(polygon);
    }
}
