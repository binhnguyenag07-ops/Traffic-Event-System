package team.traffic.domain.vehicle.pose;

import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.dynamics.Carriageway;
import team.traffic.domain.vehicle.dynamics.RoadLayout;

/**
 * The stretch of a road that lies outside the junctions at either end.
 *
 * <p>The network models a road as running from one junction's coordinate to the
 * other's, which is a fine abstraction for routing and a poor one for drawing.
 * Taken literally it says a vehicle at progress 1.0 is standing on the junction
 * node — in the middle of the crossing — and that a vehicle at progress 0.0 of
 * the next road is standing there too.</p>
 *
 * <p>That is not merely untidy, it inverts turns. A lane is the road's shape
 * pushed sideways, so at a four-way the eastbound approach ends <em>south</em>
 * of the node and the southbound exit begins <em>west</em> of it. A
 * right-turning vehicle joining those two points travels north and west —
 * backwards — and swings through a reversal on the way. Left turns happen to
 * come out forwards, which is why turning looked broken in one direction and
 * merely rough in the other.</p>
 *
 * <p>How far back to hold is not a constant. It is where the tarmac of the
 * crossing ends, so it grows with the roads meeting there: a fixed three metres
 * is fine for a lane each way and lands <em>inside</em> the turn on anything
 * wider, which reintroduces the same reversal for the outer lanes. It is taken
 * here from the widest carriageway at the junction.</p>
 *
 * <p>Everything that has to agree about where a road usefully starts and stops
 * asks this class: the stop line painted across an approach, the turning curves
 * built across a junction, and the position a vehicle's progress resolves to.
 * One definition, three consumers, no way for them to drift.</p>
 */
public final class RoadExtent {

    /**
     * Junction half-size assumed where the network offers nothing to measure —
     * a road with no geometry, or a junction nothing is attached to.
     */
    public static final double DEFAULT_JUNCTION_RADIUS_METRES = 3.0;

    /**
     * Most of a road that may be given up to its two junctions.
     *
     * <p>A slip road can be shorter than the two junctions it joins. Left
     * unbounded, the entry setback would fall beyond the exit one and the road
     * would run backwards; a vehicle on it would be drawn facing the way it
     * came. Capping each end keeps a short road short rather than reversed.</p>
     */
    private static final double MAX_SETBACK_FRACTION = 0.25;

    private final RoadNetwork network;
    private final RoadLayout roadLayout;

    public RoadExtent(RoadNetwork network, RoadLayout roadLayout) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.network = network;
        this.roadLayout = roadLayout;
    }

    /**
     * Half the width of the crossing at a junction, in metres: the furthest any
     * road's tarmac reaches from the node they share.
     */
    public double junctionRadiusMetres(String junctionId) {
        if (junctionId == null) {
            return DEFAULT_JUNCTION_RADIUS_METRES;
        }
        double radius = 0.0;
        for (Road road : network.getRoads().values()) {
            if (!junctionId.equals(road.getStartIntersectionId())
                    && !junctionId.equals(road.getEndIntersectionId())) {
                continue;
            }
            radius = Math.max(radius, carriagewayReachMetres(road));
        }
        return radius > 0 ? radius : DEFAULT_JUNCTION_RADIUS_METRES;
    }

    /** How far this road's tarmac reaches from its own centreline. */
    private double carriagewayReachMetres(Road road) {
        double halfWidth = roadLayout.roadWidthMetresOf(road) / 2.0;
        double carriageway = Math.abs(Carriageway.offsetMetres(network, road, roadLayout));
        return carriageway + halfWidth;
    }

    /** Where a road's usable stretch begins: clear of the junction behind it. */
    public double entryFraction(Road road) {
        return setbackFraction(road, road.getStartIntersectionId());
    }

    /** Where it ends, which is where the stop line is painted. */
    public double exitFraction(Road road) {
        return 1.0 - setbackFraction(road, road.getEndIntersectionId());
    }

    /**
     * A vehicle's progress along a road, as a fraction of the road's whole
     * shape. Progress 0 puts it just clear of the junction behind it and
     * progress 1 at the stop line ahead, rather than on either node.
     */
    public double drivableFraction(Road road, double progress) {
        double clamped = !Double.isFinite(progress)
                ? 0.0
                : Math.max(0.0, Math.min(1.0, progress));
        double entry = entryFraction(road);
        double exit = exitFraction(road);
        return entry + clamped * (exit - entry);
    }

    private double setbackFraction(Road road, String junctionId) {
        double lengthMetres = lengthMetresOf(road);
        if (lengthMetres <= 0) {
            return 0.0;
        }
        return Math.min(junctionRadiusMetres(junctionId) / lengthMetres, MAX_SETBACK_FRACTION);
    }

    /** The road's stored shape, or the straight line between its two junctions. */
    public List<GeoPoint> shapeOf(Road road) {
        List<GeoPoint> stored = road.getGeometry();
        if (stored != null && stored.size() >= 2) {
            return stored;
        }
        GeoPoint start = geoPointOf(network.getIntersection(road.getStartIntersectionId()));
        GeoPoint end = geoPointOf(network.getIntersection(road.getEndIntersectionId()));
        return start == null || end == null ? List.of() : List.of(start, end);
    }

    private double lengthMetresOf(Road road) {
        return PolylineGeometry.lengthMetres(shapeOf(road));
    }

    private static GeoPoint geoPointOf(Intersection intersection) {
        if (intersection == null
                || intersection.getLatitude() == null
                || intersection.getLongitude() == null) {
            return null;
        }
        return new GeoPoint(intersection.getLatitude(), intersection.getLongitude());
    }
}
