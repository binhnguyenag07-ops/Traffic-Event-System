package team.traffic.domain.network;

import java.util.List;

/**
 * A patch of ground that is neither road nor building: a park, the river, a
 * yard, a construction site.
 *
 * <p>Carried for drawing only. Nothing routes across it and no trip begins on
 * it — a trip begins at a {@link Building}. It exists so the space between the
 * roads can say what it actually is instead of being one flat colour.</p>
 *
 * @param id      stable identity, from the source way
 * @param type    what the patch is
 * @param polygon its outline, at least three points
 */
public record LandCover(String id, LandCoverType type, List<GeoPoint> polygon) {

    public LandCover {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Land cover id must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Land cover type is required");
        }
        if (polygon == null || polygon.size() < 3) {
            throw new IllegalArgumentException("Land cover needs at least three points");
        }
        polygon = List.copyOf(polygon);
    }
}
