package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * Everything within a chosen patch of the map that the physical renderer draws:
 * the lanes, the markings, the junctions, and the vehicles moving over them.
 *
 * <p>Assembled for one region around one point, so the browser receives the
 * lanes of one junction rather than the whole city. Coordinates are still
 * geographic; the API boundary converts the region to local metres once, so the
 * renderer works in a single flat frame and never touches latitude and
 * longitude.</p>
 *
 * @param origin       the point the region is centred on
 * @param radiusMetres how far around it the scene extends
 * @param lanes        lane centrelines of every road in range
 * @param stopLines    stop lines of every road in range
 * @param connectors   turning curves of every junction in range
 * @param junctions    paved areas of every junction in range
 * @param buildings    blocks between the roads, which is most of a street scene
 * @param signals      traffic lights, so a stopped queue has a visible cause
 * @param vehicles     vehicles currently within the region
 */
public record PhysicalScene(
        GeoPoint origin,
        double radiusMetres,
        List<LaneGeometry> lanes,
        List<StopLine> stopLines,
        List<LaneConnector> connectors,
        List<JunctionGeometry> junctions,
        List<SceneBuilding> buildings,
        List<SceneSignal> signals,
        List<SceneVehicle> vehicles) {

    public PhysicalScene {
        if (origin == null) {
            throw new IllegalArgumentException("Scene origin is required.");
        }
        if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
            throw new IllegalArgumentException("Scene radius must be positive.");
        }
        lanes = List.copyOf(lanes);
        stopLines = List.copyOf(stopLines);
        connectors = List.copyOf(connectors);
        junctions = List.copyOf(junctions);
        buildings = buildings == null ? List.of() : List.copyOf(buildings);
        signals = signals == null ? List.of() : List.copyOf(signals);
        vehicles = List.copyOf(vehicles);
    }
}
