package team.traffic.domain.scene;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.RoadLayout;
import team.traffic.domain.vehicle.pose.PolylineGeometry;

/**
 * Gathers the geometry and the vehicles within a chosen patch of the map into
 * one {@link PhysicalScene}.
 *
 * <p>The renderer asks for a point and a radius, not for the whole network, so
 * the browser only ever receives the lanes of what it is actually looking at. A
 * road is in range when any of its shape reaches inside the circle, a junction
 * when it sits inside it, and a vehicle when its resolved position does — three
 * checks against the same radius so nothing that touches the region is dropped
 * while something beside it is kept.</p>
 *
 * <p>Range is a straight distance test against every road's shape points. At 734
 * roads that is cheap enough that a spatial index would be premature; when the
 * network grows past what one test per road can serve, an index goes here and
 * nothing else changes.</p>
 */
public final class PhysicalSceneAssembler {

    private final RoadNetwork network;
    private final LaneGeometryBuilder geometry;

    public PhysicalSceneAssembler(RoadNetwork network) {
        this(network, new RoadDataRoadLayout());
    }

    public PhysicalSceneAssembler(RoadNetwork network, RoadLayout roadLayout) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.network = network;
        this.geometry = new LaneGeometryBuilder(network, roadLayout);
    }

    /**
     * The scene within {@code radiusMetres} of the given point, drawing on
     * {@code vehicles} for the moving objects. The vehicle list is supplied
     * rather than fetched so this stays independent of how the simulation is
     * driven; the caller resolves the current frame into scene vehicles.
     */
    public PhysicalScene sceneAround(
            double latitude, double longitude, double radiusMetres, Collection<SceneVehicle> vehicles) {
        return sceneAround(latitude, longitude, radiusMetres, vehicles, List.of());
    }

    /**
     * The scene within {@code radiusMetres} of the given point, including the
     * traffic lights the frame resolved for the junctions in range.
     */
    public PhysicalScene sceneAround(
            double latitude,
            double longitude,
            double radiusMetres,
            Collection<SceneVehicle> vehicles,
            Collection<SignalSnapshot> signals) {
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude)) {
            throw new IllegalArgumentException("Scene centre must be finite.");
        }
        if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
            throw new IllegalArgumentException("Scene radius must be positive.");
        }
        GeoPoint origin = new GeoPoint(latitude, longitude);

        List<LaneGeometry> lanes = new ArrayList<>();
        List<StopLine> stopLines = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (!roadInRange(road, origin, radiusMetres)) {
                continue;
            }
            lanes.addAll(geometry.lanesOf(road));
            StopLine stopLine = geometry.stopLineOf(road);
            if (stopLine != null) {
                stopLines.add(stopLine);
            }
        }

        Map<String, SignalSnapshot> signalByJunction = new LinkedHashMap<>();
        if (signals != null) {
            signals.forEach(signal -> signalByJunction.put(signal.junctionId(), signal));
        }

        List<LaneConnector> connectors = new ArrayList<>();
        List<JunctionGeometry> junctions = new ArrayList<>();
        List<SceneSignal> sceneSignals = new ArrayList<>();
        for (Intersection intersection : network.getIntersections().values()) {
            if (!intersectionInRange(intersection, origin, radiusMetres)) {
                continue;
            }
            connectors.addAll(geometry.connectorsAt(intersection.getId()));
            junctions.add(geometry.junctionGeometryOf(intersection.getId()));
            SignalSnapshot snapshot = signalByJunction.get(intersection.getId());
            if (snapshot != null) {
                sceneSignals.add(signalAt(snapshot));
            }
        }

        List<SceneBuilding> buildings = network.getBuildings().values().stream()
                .filter(building -> buildingInRange(building, origin, radiusMetres))
                .map(PhysicalSceneAssembler::toSceneBuilding)
                .toList();

        List<SceneVehicle> inRange = vehicles == null ? List.of() : vehicles.stream()
                .filter(vehicle -> withinRadius(
                        origin, new GeoPoint(vehicle.latitude(), vehicle.longitude()), radiusMetres))
                .toList();

        return new PhysicalScene(
                origin, radiusMetres, lanes, stopLines, connectors, junctions,
                buildings, sceneSignals, inRange);
    }

    /** Places one head per controlled approach, on that approach's stop line. */
    private SceneSignal signalAt(SignalSnapshot snapshot) {
        List<SceneSignal.Approach> approaches = new ArrayList<>();
        for (Road road : network.getRoads().values()) {
            if (!snapshot.junctionId().equals(road.getEndIntersectionId())) {
                continue;
            }
            StopLine stopLine = geometry.stopLineOf(road);
            if (stopLine == null) {
                continue;
            }
            approaches.add(new SceneSignal.Approach(
                    road.getId(),
                    kerbEndOf(stopLine),
                    snapshot.greenIncomingRoadIds().contains(road.getId()),
                    snapshot.secondsRemaining()));
        }
        return new SceneSignal(snapshot.junctionId(), approaches);
    }

    /**
     * The kerbside end of a stop line, which is where a signal actually stands.
     *
     * <p>Placed at the midpoint it sat in the middle of the carriageway, reading
     * as an obstruction across the road rather than a light beside it.</p>
     */
    private static GeoPoint kerbEndOf(StopLine stopLine) {
        return stopLine.from();
    }

    private static SceneBuilding toSceneBuilding(Building building) {
        return new SceneBuilding(
                building.getId(),
                building.getName(),
                building.getType(),
                new GeoPoint(building.getLatitude(), building.getLongitude()),
                building.getFootprint());
    }

    private static boolean buildingInRange(Building building, GeoPoint origin, double radiusMetres) {
        if (building.getLatitude() == null || building.getLongitude() == null) {
            return false;
        }
        GeoPoint centre = new GeoPoint(building.getLatitude(), building.getLongitude());
        if (withinRadius(origin, centre, radiusMetres)) {
            return true;
        }
        // A large block whose centre sits outside still shows if a wall reaches in.
        return building.getFootprint().stream()
                .anyMatch(point -> withinRadius(origin, point, radiusMetres));
    }

    private boolean roadInRange(Road road, GeoPoint origin, double radiusMetres) {
        for (GeoPoint point : geometry.shapeOf(road)) {
            if (withinRadius(origin, point, radiusMetres)) {
                return true;
            }
        }
        return false;
    }

    private static boolean intersectionInRange(
            Intersection intersection, GeoPoint origin, double radiusMetres) {
        if (intersection.getLatitude() == null || intersection.getLongitude() == null) {
            return false;
        }
        return withinRadius(
                origin,
                new GeoPoint(intersection.getLatitude(), intersection.getLongitude()),
                radiusMetres);
    }

    private static boolean withinRadius(GeoPoint origin, GeoPoint point, double radiusMetres) {
        return PolylineGeometry.lengthMetres(List.of(origin, point)) <= radiusMetres;
    }
}
