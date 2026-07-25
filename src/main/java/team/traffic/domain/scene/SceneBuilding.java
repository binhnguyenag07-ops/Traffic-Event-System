package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.GeoPoint;

/**
 * A building as the physical renderer draws it.
 *
 * <p>Roads through empty space do not read as a city. The blocks between them
 * are most of what a street scene actually is, and they are also what tells a
 * viewer where they are: a hospital, a school and a row of houses look
 * different and mean different things.</p>
 *
 * <p>The outline is optional. A map that records only where a building stands
 * still renders, as a small block at that point, rather than not at all.</p>
 *
 * @param id        stable identity
 * @param name      display name
 * @param type      what the building is for, which decides its colour
 * @param centre    where it stands
 * @param footprint ground outline, empty when the map has none
 */
public record SceneBuilding(
        String id,
        String name,
        BuildingType type,
        GeoPoint centre,
        List<GeoPoint> footprint) {

    public SceneBuilding {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Building id must not be blank.");
        }
        if (centre == null) {
            throw new IllegalArgumentException("Building centre is required.");
        }
        footprint = footprint == null ? List.of() : List.copyOf(footprint);
    }

    public boolean hasFootprint() {
        return footprint.size() >= 3;
    }
}
