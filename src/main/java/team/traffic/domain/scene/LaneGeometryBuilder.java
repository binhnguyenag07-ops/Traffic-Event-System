package team.traffic.domain.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.RoadLayout;
import team.traffic.domain.vehicle.pose.PolylineGeometry;

/**
 * Turns the network's centreline model into the lane-level geometry a physical
 * renderer draws.
 *
 * <p>The model stores a road as one line down its middle and a lane count. The
 * renderer needs each lane as its own line, a stop line across each approach,
 * curves joining lanes across junctions, and a paved area under each junction.
 * All of it is derived here from the road's shape and its lane layout, using the
 * same {@link RoadLayout#laneCentreOffsetMetres} the simulation places vehicles
 * with, so what is drawn and what is simulated cannot drift apart.</p>
 *
 * <p>Lane centrelines are produced by offsetting each shape point sideways along
 * the road's local normal. On gentle city roads that is accurate; on a hairpin
 * it would pinch the inner lane, which is a known limit of per-vertex offsetting
 * and not worth a full mitring pass until a map needs it.</p>
 *
 * <p>This class reads the network and never mutates it, so it does not disturb
 * the network aggregate. It lives outside {@code domain.network} on purpose: it
 * depends on {@code RoadLayout} and {@code PolylineGeometry}, both of which sit
 * in {@code domain.vehicle}, and a geometry helper under {@code network} that
 * reached up into {@code vehicle} would invert the layering.</p>
 */
public final class LaneGeometryBuilder {

    /** How far back from the junction the stop line sits, in metres. */
    private static final double STOP_LINE_SETBACK_METRES = 3.0;
    /** Points sampled along each junction turning curve. */
    private static final int CONNECTOR_SAMPLE_COUNT = 8;

    private final RoadNetwork network;
    private final RoadLayout roadLayout;

    public LaneGeometryBuilder(RoadNetwork network) {
        this(network, new RoadDataRoadLayout());
    }

    public LaneGeometryBuilder(RoadNetwork network, RoadLayout roadLayout) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.network = network;
        this.roadLayout = roadLayout;
    }

    /**
     * The centrelines of every lane of one road, or an empty list when the road
     * carries no geographic shape (a grid scenario without coordinates).
     */
    public List<LaneGeometry> lanesOf(Road road) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        List<GeoPoint> shape = shapeOf(road);
        if (shape.size() < 2) {
            return List.of();
        }
        int laneCount = roadLayout.laneCountOf(road);
        double laneWidth = roadLayout.laneWidthMetresOf(road);
        boolean oneway = !hasOpposite(road);
        double carriageway = carriagewayOffsetOf(road);
        List<LaneGeometry> lanes = new ArrayList<>(laneCount);
        for (int index = 0; index < laneCount; index++) {
            double offset = RoadLayout.laneCentreOffsetMetres(index, laneCount, laneWidth) + carriageway;
            lanes.add(new LaneGeometry(
                    road.getId(), index, laneWidth, offsetPolyline(shape, offset), oneway));
        }
        return lanes;
    }

    /**
     * How far a road's carriageway sits from the shared centreline.
     *
     * <p>A two-way street is two directed roads over one line of geometry. Left
     * centred, both sides land on that line and their traffic is drawn on top of
     * itself — vehicles going opposite ways appear to occupy the same tarmac.
     * Each direction is therefore pushed to its own side, and since the offset
     * convention is positive to the right of travel, that is also the side
     * traffic here drives on.</p>
     *
     * <p>A one-way road keeps the centreline: there is no opposing carriageway
     * to make room for.</p>
     */
    private double carriagewayOffsetOf(Road road) {
        if (!hasOpposite(road)) {
            return 0.0;
        }
        return roadLayout.roadWidthMetresOf(road) / 2.0;
    }

    /**
     * The stop line across a road's exit, or {@code null} when the road has no
     * geographic shape to place it on.
     */
    public StopLine stopLineOf(Road road) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        List<GeoPoint> shape = shapeOf(road);
        if (shape.size() < 2) {
            return null;
        }
        double length = PolylineGeometry.lengthMetres(shape);
        double fraction = length <= 0 ? 1.0 : Math.max(0.0, 1.0 - STOP_LINE_SETBACK_METRES / length);
        return crossSectionAt(road, shape, fraction);
    }

    /** A line across one carriageway at a fraction of its stored geometry. */
    private StopLine crossSectionAt(Road road, List<GeoPoint> shape, double fraction) {
        GeoPoint at = PolylineGeometry.pointAt(shape, fraction);
        double heading = PolylineGeometry.bearingAt(shape, fraction);
        // Centred on this direction's carriageway, not on the shared centreline,
        // or a two-way street would be barred across both sides at once.
        double carriageway = carriagewayOffsetOf(road);
        double halfWidth = roadLayout.roadWidthMetresOf(road) / 2.0;
        return new StopLine(
                road.getId(),
                PolylineGeometry.offsetMetres(at, heading, carriageway + halfWidth),
                PolylineGeometry.offsetMetres(at, heading, carriageway - halfWidth));
    }

    /**
     * Turning curves across one junction, one per lane pairing between each
     * road arriving and each road leaving, skipping the U-turn back down the
     * road just travelled.
     */
    public List<LaneConnector> connectorsAt(String junctionId) {
        Intersection junction = network.getIntersection(junctionId);
        GeoPoint centre = geoOf(junction);
        if (centre == null) {
            return List.of();
        }
        List<LaneConnector> connectors = new ArrayList<>();
        for (Road incoming : roadsEndingAt(junctionId)) {
            List<LaneGeometry> inLanes = lanesOf(incoming);
            for (Road outgoing : roadsStartingAt(junctionId)) {
                if (isReverseOf(outgoing, incoming)) {
                    continue;
                }
                List<LaneGeometry> outLanes = lanesOf(outgoing);
                int pairs = Math.min(inLanes.size(), outLanes.size());
                for (int lane = 0; lane < pairs; lane++) {
                    connectors.add(connector(inLanes.get(lane), outLanes.get(lane), centre));
                }
            }
        }
        return connectors;
    }

    /**
     * The paved area of a junction as a polygon, built from where the roads
     * meeting there reach it. Empty when fewer than three edges bound it.
     */
    public JunctionGeometry junctionGeometryOf(String junctionId) {
        Intersection junction = network.getIntersection(junctionId);
        GeoPoint centre = geoOf(junction);
        if (centre == null) {
            return new JunctionGeometry(junctionId, List.of());
        }
        List<GeoPoint> corners = new ArrayList<>();
        for (Road road : roadsTouching(junctionId)) {
            StopLine edge = junctionEdgeOf(road, junctionId);
            if (edge != null) {
                corners.add(edge.from());
                corners.add(edge.to());
            }
        }
        return new JunctionGeometry(junctionId, orderAround(centre, corners));
    }

    /**
     * The road cross-section beside the requested junction.
     *
     * A normal stop line is always measured back from the road's end. That is
     * correct for an incoming approach, but an outgoing road touches this
     * junction at the start of its geometry. Reusing {@link #stopLineOf(Road)}
     * for both ends pulled the far end of every outgoing road into the pavement
     * polygon, creating junctions hundreds of metres wide.
     */
    private StopLine junctionEdgeOf(Road road, String junctionId) {
        List<GeoPoint> shape = shapeOf(road);
        if (shape.size() < 2) {
            return null;
        }
        double length = PolylineGeometry.lengthMetres(shape);
        double setback = length <= 0
                ? 0.0
                : Math.min(1.0, STOP_LINE_SETBACK_METRES / length);
        double fraction;
        if (junctionId.equals(road.getStartIntersectionId())) {
            fraction = setback;
        } else if (junctionId.equals(road.getEndIntersectionId())) {
            fraction = 1.0 - setback;
        } else {
            return null;
        }
        return crossSectionAt(road, shape, fraction);
    }

    private LaneConnector connector(LaneGeometry from, LaneGeometry to, GeoPoint control) {
        GeoPoint start = from.centreline().get(from.centreline().size() - 1);
        GeoPoint end = to.centreline().get(0);
        return new LaneConnector(
                from.roadId(), from.laneIndex(),
                to.roadId(), to.laneIndex(),
                quadraticBezier(start, control, end));
    }

    private static List<GeoPoint> quadraticBezier(GeoPoint start, GeoPoint control, GeoPoint end) {
        List<GeoPoint> curve = new ArrayList<>(CONNECTOR_SAMPLE_COUNT + 1);
        for (int step = 0; step <= CONNECTOR_SAMPLE_COUNT; step++) {
            double t = (double) step / CONNECTOR_SAMPLE_COUNT;
            double oneMinusT = 1.0 - t;
            double a = oneMinusT * oneMinusT;
            double b = 2 * oneMinusT * t;
            double c = t * t;
            curve.add(new GeoPoint(
                    a * start.latitude() + b * control.latitude() + c * end.latitude(),
                    a * start.longitude() + b * control.longitude() + c * end.longitude()));
        }
        return curve;
    }

    private List<GeoPoint> offsetPolyline(List<GeoPoint> shape, double offsetMetres) {
        if (offsetMetres == 0) {
            return List.copyOf(shape);
        }
        List<GeoPoint> offset = new ArrayList<>(shape.size());
        for (int index = 0; index < shape.size(); index++) {
            offset.add(PolylineGeometry.offsetMetres(shape.get(index), vertexHeading(shape, index), offsetMetres));
        }
        return offset;
    }

    /** Direction the road runs at one shape point, averaging the segments meeting there. */
    private static double vertexHeading(List<GeoPoint> shape, int index) {
        if (index == 0) {
            return PolylineGeometry.bearingBetween(shape.get(0), shape.get(1));
        }
        if (index == shape.size() - 1) {
            return PolylineGeometry.bearingBetween(shape.get(index - 1), shape.get(index));
        }
        double incoming = PolylineGeometry.bearingBetween(shape.get(index - 1), shape.get(index));
        double outgoing = PolylineGeometry.bearingBetween(shape.get(index), shape.get(index + 1));
        return PolylineGeometry.interpolateBearing(incoming, outgoing, 0.5);
    }

    /** Orders points into a simple polygon by angle around a centre. */
    private static List<GeoPoint> orderAround(GeoPoint centre, List<GeoPoint> points) {
        List<GeoPoint> ordered = new ArrayList<>(points);
        ordered.sort(Comparator.comparingDouble(point -> PolylineGeometry.bearingBetween(centre, point)));
        return ordered;
    }

    /** The road's drawable shape, package-visible so the scene assembler can range-check it. */
    List<GeoPoint> shapeOf(Road road) {
        List<GeoPoint> stored = road.getGeometry();
        if (stored != null && stored.size() >= 2) {
            return stored;
        }
        GeoPoint start = geoOf(network.getIntersection(road.getStartIntersectionId()));
        GeoPoint end = geoOf(network.getIntersection(road.getEndIntersectionId()));
        return start == null || end == null ? List.of() : List.of(start, end);
    }

    private boolean hasOpposite(Road road) {
        return network.getRoads().values().stream().anyMatch(other -> isReverseOf(other, road));
    }

    private static boolean isReverseOf(Road candidate, Road road) {
        return candidate != road
                && candidate.getStartIntersectionId().equals(road.getEndIntersectionId())
                && candidate.getEndIntersectionId().equals(road.getStartIntersectionId());
    }

    private List<Road> roadsEndingAt(String junctionId) {
        return network.getRoads().values().stream()
                .filter(road -> junctionId.equals(road.getEndIntersectionId()))
                .toList();
    }

    private List<Road> roadsStartingAt(String junctionId) {
        return network.getRoads().values().stream()
                .filter(road -> junctionId.equals(road.getStartIntersectionId()))
                .toList();
    }

    private List<Road> roadsTouching(String junctionId) {
        return network.getRoads().values().stream()
                .filter(road -> junctionId.equals(road.getStartIntersectionId())
                        || junctionId.equals(road.getEndIntersectionId()))
                .toList();
    }

    private static GeoPoint geoOf(Intersection intersection) {
        if (intersection == null
                || intersection.getLatitude() == null
                || intersection.getLongitude() == null) {
            return null;
        }
        return new GeoPoint(intersection.getLatitude(), intersection.getLongitude());
    }
}
