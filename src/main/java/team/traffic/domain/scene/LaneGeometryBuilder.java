package team.traffic.domain.scene;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.dynamics.Carriageway;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.RoadLayout;
import team.traffic.domain.vehicle.pose.PolylineGeometry;
import team.traffic.domain.vehicle.pose.RoadExtent;

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

    /** Points sampled along each junction turning curve. */
    private static final int CONNECTOR_SAMPLE_COUNT = 8;
    /**
     * Below this endpoint separation there is no drawable gap to curve across.
     * Trying to force a curve through the junction centre would make the path
     * leave a lane endpoint and return to it, reversing its tangent.
     */
    private static final double MIN_CURVED_CONNECTOR_METRES = 0.25;
    /** A sampled connector must never hide a sharp correction in one vertex. */
    private static final double MAX_CONNECTOR_VERTEX_TURN_DEGREES = 45.0;
    /** A connector must leave and join its lanes without an endpoint cusp. */
    private static final double MAX_CONNECTOR_TANGENT_ERROR_DEGREES = 45.0;
    /**
     * Hermite handle lengths tried from broadest to tightest. Imported OSM
     * junctions can be wider than a very short road beside them; one fixed
     * one-third handle is not robust for both ordinary and compact junctions.
     */
    private static final double[] CONNECTOR_HANDLE_RATIOS = {
        0.5, 1.0 / 3.0, 0.25, 0.2, 0.125, 0.075
    };

    private final RoadNetwork network;
    private final RoadLayout roadLayout;
    /**
     * Where each road usefully starts and stops. Shared with the pose resolver,
     * so the painted stop line, the turning curves, and the place traffic
     * actually waits cannot drift apart.
     */
    private final RoadExtent extent;

    public LaneGeometryBuilder(RoadNetwork network) {
        this(network, new RoadDataRoadLayout());
    }

    public LaneGeometryBuilder(RoadNetwork network, RoadLayout roadLayout) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.network = network;
        this.roadLayout = roadLayout;
        this.extent = new RoadExtent(network, roadLayout);
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
     * How far a road's carriageway sits from the shared centreline. Delegated so
     * the painted lanes and the vehicles standing on them cannot disagree — see
     * {@link Carriageway}.
     */
    private double carriagewayOffsetOf(Road road) {
        return Carriageway.offsetMetres(network, road, roadLayout);
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
        return crossSectionAt(road, shape, extent.exitFraction(road));
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
     * Turning curves across one junction, one deterministic lane continuation
     * for every incoming lane of each legal road transition. The authoritative
     * movement graph therefore keeps routing, arbitration, and rendered U-turn
     * geometry in agreement.
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
                boolean movementExists = network.getMovementGraph()
                        .find(junctionId, incoming.getId(), outgoing.getId())
                        .filter(movement ->
                                movement.permission() == MovementPermission.ALLOWED)
                        .isPresent();
                if (!movementExists) {
                    continue;
                }
                List<LaneGeometry> outLanes = lanesOf(outgoing);
                if (inLanes.isEmpty() || outLanes.isEmpty()) {
                    continue;
                }
                double startFraction = extent.exitFraction(incoming);
                double endFraction = extent.entryFraction(outgoing);
                // Publish exactly the deterministic continuation used by
                // RoadOccupancy: preserve the incoming index, clamping only when
                // the outgoing road has fewer lanes. This gives every simulated
                // crossing one exact connector without the quadratic payload and
                // draw cost of every possible lane pair.
                for (LaneGeometry inLane : inLanes) {
                    int outIndex = Math.min(inLane.laneIndex(), outLanes.size() - 1);
                    connectors.add(connector(
                            inLane, outLanes.get(outIndex),
                            centre, startFraction, endFraction));
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
        double fraction;
        if (junctionId.equals(road.getStartIntersectionId())) {
            fraction = extent.entryFraction(road);
        } else if (junctionId.equals(road.getEndIntersectionId())) {
            fraction = extent.exitFraction(road);
        } else {
            return null;
        }
        return crossSectionAt(road, shape, fraction);
    }

    private LaneConnector connector(
            LaneGeometry from,
            LaneGeometry to,
            GeoPoint junctionCentre,
            double startFraction,
            double endFraction) {
        return new LaneConnector(
                from.roadId(), from.laneIndex(),
                to.roadId(), to.laneIndex(),
                connectorCurve(
                        from.centreline(), to.centreline(),
                        junctionCentre, startFraction, endFraction));
    }

    /**
     * Joins two lane centrelines across the junction between them.
     *
     * <p>The curve runs from the incoming lane's stop line to the outgoing
     * lane's exit line, not from one lane's last point to the other's first.
     * Those two points both sit on the junction node, and because a lane is the
     * road shape pushed sideways they sit on <em>opposite</em> sides of it: at a
     * four-way the eastbound approach ends south of the node and the southbound
     * exit begins west of it. Joining them sent every right turn backwards
     * through a reversal. Measured on the four-way case, a motorcycle turning
     * west-to-south swung 94 degrees in a single animation frame. Starting and
     * ending clear of the junction — the same {@link RoadExtent} the stop line
     * and the vehicle poses use — makes the turn come out forwards.</p>
     *
     * <p>A cubic uses the lane tangents at those points. Several local handle
     * lengths are tried because a single fixed ratio fails beside short OSM road
     * segments. Every candidate, including the centre quadratic fallback, must
     * match both endpoint tangents and avoid sharp sampled corrections. A
     * straight bridge is the final finite, loop-free fallback.</p>
     */
    private static List<GeoPoint> connectorCurve(
            List<GeoPoint> incoming,
            List<GeoPoint> outgoing,
            GeoPoint junctionCentre,
            double startFraction,
            double endFraction) {
        GeoPoint start = PolylineGeometry.pointAt(incoming, startFraction);
        GeoPoint end = PolylineGeometry.pointAt(outgoing, endFraction);
        if (start == null || end == null) {
            return List.of();
        }
        double gapMetres = PolylineGeometry.lengthMetres(List.of(start, end));
        if (gapMetres < MIN_CURVED_CONNECTOR_METRES) {
            return List.of(start, end);
        }

        double incomingBearing = PolylineGeometry.bearingAt(incoming, startFraction);
        double outgoingBearing = PolylineGeometry.bearingAt(outgoing, endFraction);
        for (double handleRatio : CONNECTOR_HANDLE_RATIOS) {
            double handleMetres = gapMetres * handleRatio;
            GeoPoint firstControl = PolylineGeometry.advanceMetres(
                    start, incomingBearing, handleMetres);
            GeoPoint secondControl = PolylineGeometry.advanceMetres(
                    end, outgoingBearing, -handleMetres);
            List<GeoPoint> candidate = cubicBezier(start, firstControl, secondControl, end);
            if (isSafeConnectorCurve(candidate, incomingBearing, outgoingBearing)) {
                return candidate;
            }
        }

        List<GeoPoint> centreCurve = quadraticBezier(start, junctionCentre, end);
        return isSafeConnectorCurve(centreCurve, incomingBearing, outgoingBearing)
                ? centreCurve
                : List.of(start, end);
    }

    private static List<GeoPoint> cubicBezier(
            GeoPoint start, GeoPoint firstControl, GeoPoint secondControl, GeoPoint end) {
        List<GeoPoint> curve = new ArrayList<>(CONNECTOR_SAMPLE_COUNT + 1);
        for (int step = 0; step <= CONNECTOR_SAMPLE_COUNT; step++) {
            double t = (double) step / CONNECTOR_SAMPLE_COUNT;
            double oneMinusT = 1.0 - t;
            double a = oneMinusT * oneMinusT * oneMinusT;
            double b = 3 * oneMinusT * oneMinusT * t;
            double c = 3 * oneMinusT * t * t;
            double d = t * t * t;
            curve.add(new GeoPoint(
                    a * start.latitude()
                            + b * firstControl.latitude()
                            + c * secondControl.latitude()
                            + d * end.latitude(),
                    a * start.longitude()
                            + b * firstControl.longitude()
                            + c * secondControl.longitude()
                            + d * end.longitude()));
        }
        return curve;
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

    /**
     * Rejects zero-length segments, endpoint cusps, and sharp internal turns.
     *
     * <p>Progress along the start-to-end chord is deliberately not required.
     * Offset lane endpoints at a compact turn can make a correct curve travel
     * briefly against that chord even while it follows both road tangents.
     * Chord monotonicity used to reject that valid cubic, after which a
     * centre-point quadratic with a 90-degree cusp was accepted.</p>
     */
    private static boolean isSafeConnectorCurve(
            List<GeoPoint> curve,
            double incomingBearing,
            double outgoingBearing) {
        if (curve == null || curve.size() < 2) {
            return false;
        }
        List<Double> bearings = new ArrayList<>(curve.size() - 1);
        for (int index = 1; index < curve.size(); index++) {
            GeoPoint previous = curve.get(index - 1);
            GeoPoint current = curve.get(index);
            if (PolylineGeometry.lengthMetres(List.of(previous, current)) <= 0) {
                return false;
            }
            bearings.add(PolylineGeometry.bearingBetween(previous, current));
        }
        if (Math.abs(PolylineGeometry.bearingDifference(
                incomingBearing, bearings.get(0))) > MAX_CONNECTOR_TANGENT_ERROR_DEGREES) {
            return false;
        }
        if (Math.abs(PolylineGeometry.bearingDifference(
                bearings.get(bearings.size() - 1), outgoingBearing))
                > MAX_CONNECTOR_TANGENT_ERROR_DEGREES) {
            return false;
        }
        for (int index = 1; index < bearings.size(); index++) {
            if (Math.abs(PolylineGeometry.bearingDifference(
                    bearings.get(index - 1), bearings.get(index)))
                            > MAX_CONNECTOR_VERTEX_TURN_DEGREES) {
                return false;
            }
        }
        return true;
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
        return Carriageway.hasOpposite(network, road);
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
