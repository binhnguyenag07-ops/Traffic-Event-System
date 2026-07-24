package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * The paved area of a junction, as a polygon a renderer can fill.
 *
 * <p>A junction is a point in the model, but on the ground it is a patch of
 * tarmac wide enough for the roads meeting there. Drawing it as a filled area
 * under the lane markings is what makes an intersection look like one rather
 * than like lines crossing in mid-air. The polygon is the hull of where the
 * roads' carriageways reach the junction, so it grows with the number and width
 * of the roads.</p>
 *
 * @param junctionId the intersection this pavement belongs to
 * @param pavement   the boundary polygon, at least three points, or empty when
 *                   too few roads meet to enclose an area
 */
public record JunctionGeometry(String junctionId, List<GeoPoint> pavement) {

    public JunctionGeometry {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id must not be blank.");
        }
        pavement = pavement == null ? List.of() : List.copyOf(pavement);
    }

    /** Whether the pavement encloses an area, which needs at least three points. */
    public boolean hasArea() {
        return pavement.size() >= 3;
    }
}
