package team.traffic.domain.vehicle.pose;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.Carriageway;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.RoadLayout;

/**
 * Works out where a vehicle is on the map and which way it faces.
 *
 * <p>A vehicle on a road is placed by sampling that road's shape at its
 * progress. A vehicle waiting to cross a junction is the awkward case: it holds
 * no road at all, so it has no shape to sample. Left unhandled it has no
 * position and no heading, and a renderer has nothing to go on but the junction
 * itself — which is why waiting vehicles used to snap round to face north.
 * Junction arbitration made that worse rather than better, because vehicles now
 * queue at stop lines instead of crossing straight through.</p>
 *
 * <p>Such a vehicle is placed at the junction, still pointing the way it was
 * travelling when it arrived. Its route is what supplies that: the step before
 * the one it is waiting on is the road it just came off. A vehicle that has not
 * started yet faces the road it is about to take instead.</p>
 *
 * <p>Placement across the road comes from the vehicle rather than from its lane
 * index and a road layout. That removes a second way of arriving at the same
 * number: the resolver used to consult a layout of its own choosing, so a
 * scenario whose simulation ran on a different layout would be drawn in lanes
 * of a width it was never simulated with. It also means a vehicle half way
 * through a lane change is drawn half way across, which a lane index cannot
 * express.</p>
 */
public final class VehiclePoseResolver {

    /** Bumper-to-bumper clearance between vehicles queueing at a junction. */
    private static final double QUEUE_GAP_METRES = 1.5;

    private final RoadLayout roadLayout;

    public VehiclePoseResolver() {
        this(new RoadDataRoadLayout());
    }

    public VehiclePoseResolver(RoadLayout roadLayout) {
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.roadLayout = roadLayout;
    }

    /**
     * Resolves the pose of one vehicle, or an empty result when the map carries
     * no geographic coordinates for it. Grid scenarios without latitude and
     * longitude fall into that case.
     */
    public Optional<VehiclePose> resolve(Vehicle vehicle, RoadNetwork network) {
        if (vehicle == null || network == null) {
            return Optional.empty();
        }

        Road occupiedRoad = vehicle.getOccupiedRoadId() == null
                ? null
                : network.getRoad(vehicle.getOccupiedRoadId());
        return occupiedRoad == null
                ? poseInJunction(vehicle, network)
                : poseOnRoad(vehicle, network, occupiedRoad);
    }

    private Optional<VehiclePose> poseOnRoad(Vehicle vehicle, RoadNetwork network, Road road) {
        List<GeoPoint> shape = shapeOf(road, network);
        GeoPoint position = PolylineGeometry.pointAt(shape, vehicle.getRoadProgress());
        if (position == null) {
            return Optional.empty();
        }
        double heading = PolylineGeometry.bearingAt(shape, vehicle.getRoadProgress());
        // The vehicle's own lateral offset, not one derived from its lane
        // index: a vehicle part-way through a lane change is genuinely between
        // two lanes, and deriving the offset from the index would jump it
        // across the full lane width in a single frame.
        double lateralOffset = vehicle.getLateralOffsetMetres();
        // Plus the side of the street this direction drives on. Without it a
        // two-way road puts both directions on the shared centreline, so
        // oncoming traffic drives through itself and every vehicle is drawn
        // beside the lane painted for it rather than on it.
        double carriageway = Carriageway.offsetMetres(network, road, roadLayout);
        position = PolylineGeometry.offsetMetres(position, heading, lateralOffset + carriageway);
        return Optional.of(new VehiclePose(
                position,
                heading,
                vehicle.getLength(),
                vehicle.getWidth(),
                road.getId(),
                vehicle.getLaneIndex(),
                lateralOffset,
                false));
    }

    /**
     * A vehicle holding no road: waiting to cross, or not yet away.
     *
     * <p>Placing it on the junction's own coordinate is the obvious thing and it
     * is wrong twice over. The point is the middle of the crossing, so a vehicle
     * queueing to enter is drawn already in it; and every vehicle at that
     * junction gets the same coordinate, so a scenario that starts ten vehicles
     * at one node opens with ten bodies in a single heap.</p>
     *
     * <p>So it is put on tarmac instead: at the exit of the road it arrived on —
     * which is where the stop line is — or, before its first move, at the entry
     * of the road it is about to take. Both carry the vehicle's lane and its
     * side of the street, so it lines up with the queue it belongs to.</p>
     */
    private Optional<VehiclePose> poseInJunction(Vehicle vehicle, RoadNetwork network) {
        Road arrivedOn = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex() - 1);
        if (arrivedOn != null) {
            return approachPose(vehicle, network, arrivedOn, 1.0);
        }
        Road aboutToTake = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex());
        if (aboutToTake != null) {
            return approachPose(vehicle, network, aboutToTake, 0.0);
        }

        // No road and no route: the vehicle has not joined the traffic yet, so
        // there is no honest place to put it. Drawing it on the junction's own
        // coordinate was the old answer, and it piled every vehicle of a
        // scenario into one heap on the first frame. An empty pose leaves it
        // out of the scene until it has a route to stand on.
        return Optional.empty();
    }

    /**
     * Every vehicle's pose, with those queueing at a junction strung out along
     * their approach instead of sharing one point.
     *
     * <p>Resolving one vehicle at a time cannot do this: whether a waiting
     * vehicle is first or fourth in its queue is a fact about the other
     * vehicles. A scenario that starts ten at one junction is the common case,
     * and drawn individually all ten land on the same coordinate.</p>
     *
     * <p>Order within a queue is by vehicle id, so the same captured state
     * always draws the same way.</p>
     */
    public Map<String, VehiclePose> resolveAll(List<Vehicle> vehicles, RoadNetwork network) {
        Map<String, VehiclePose> poses = new LinkedHashMap<>();
        if (vehicles == null || network == null) {
            return poses;
        }
        Map<String, Double> queuedMetres = new HashMap<>();
        for (Vehicle vehicle : vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getId))
                .toList()) {
            Approach approach = approachOf(vehicle, network);
            if (approach == null) {
                resolve(vehicle, network).ifPresent(pose -> poses.put(vehicle.getId(), pose));
                continue;
            }
            String slot = approach.road().getId() + "|" + vehicle.getLaneIndex()
                    + "|" + approach.fraction();
            double alreadyQueued = queuedMetres.getOrDefault(slot, 0.0);
            approachPose(vehicle, network, approach.road(), approach.fraction(), alreadyQueued)
                    .ifPresent(pose -> poses.put(vehicle.getId(), pose));
            queuedMetres.put(slot, alreadyQueued + vehicle.getLength() + QUEUE_GAP_METRES);
        }
        return poses;
    }

    /** Which road end a road-less vehicle belongs to, or null if it holds a road. */
    private Approach approachOf(Vehicle vehicle, RoadNetwork network) {
        if (vehicle.getOccupiedRoadId() != null
                && network.getRoad(vehicle.getOccupiedRoadId()) != null) {
            return null;
        }
        Road arrivedOn = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex() - 1);
        if (arrivedOn != null) return new Approach(arrivedOn, 1.0);
        Road aboutToTake = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex());
        return aboutToTake == null ? null : new Approach(aboutToTake, 0.0);
    }

    private record Approach(Road road, double fraction) {}

    private Optional<VehiclePose> approachPose(
            Vehicle vehicle, RoadNetwork network, Road road, double fraction) {
        return approachPose(vehicle, network, road, fraction, 0.0);
    }

    /**
     * The vehicle placed on one end of a road, in its own lane and carriageway,
     * held back {@code queuedMetres} along the road by the vehicles ahead of it.
     *
     * <p>A queue at a road's exit extends backwards from the stop line. One at a
     * road's entry — vehicles that have not moved off yet — extends forwards
     * instead, because backwards from the entry is off the end of the road.</p>
     */
    private Optional<VehiclePose> approachPose(
            Vehicle vehicle, RoadNetwork network, Road road, double fraction, double queuedMetres) {
        List<GeoPoint> shape = shapeOf(road, network);
        double lengthMetres = PolylineGeometry.lengthMetres(shape);
        if (lengthMetres > 0 && queuedMetres > 0) {
            double shift = Math.min(queuedMetres / lengthMetres, 1.0);
            fraction = fraction >= 1.0 ? 1.0 - shift : Math.min(1.0, fraction + shift);
        }
        GeoPoint position = PolylineGeometry.pointAt(shape, fraction);
        if (position == null) {
            return Optional.empty();
        }
        double heading = PolylineGeometry.bearingAt(shape, fraction);
        double lateralOffset = roadLayout.laneCentreOffsetMetresOf(road, vehicle.getLaneIndex());
        double carriageway = Carriageway.offsetMetres(network, road, roadLayout);
        return Optional.of(new VehiclePose(
                PolylineGeometry.offsetMetres(position, heading, lateralOffset + carriageway),
                heading,
                vehicle.getLength(),
                vehicle.getWidth(),
                null,
                vehicle.getLaneIndex(),
                lateralOffset,
                true));
    }

    /**
     * Heading of a vehicle that holds no road: the way it was going when it
     * arrived, or, before its first move, the way it is about to go.
     */
    private double headingAtJunction(Vehicle vehicle, RoadNetwork network) {
        Road arrivedOn = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex() - 1);
        if (arrivedOn != null) {
            return PolylineGeometry.bearingAt(shapeOf(arrivedOn, network), 1.0);
        }

        Road aboutToTake = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex());
        if (aboutToTake != null) {
            return PolylineGeometry.bearingAt(shapeOf(aboutToTake, network), 0.0);
        }
        return 0.0;
    }

    private Road routeRoadAt(Vehicle vehicle, RoadNetwork network, int stepIndex) {
        Route route = vehicle.getCurrentRoute();
        if (route == null || stepIndex < 0 || stepIndex >= route.getStepCount()) {
            return null;
        }
        RouteStep step = route.getSteps().get(stepIndex);
        return network.getRoad(step.getRoadId());
    }

    /**
     * The road's stored shape, falling back to a straight line between its two
     * junctions when a scenario carries no detailed geometry.
     */
    private List<GeoPoint> shapeOf(Road road, RoadNetwork network) {
        List<GeoPoint> stored = road.getGeometry();
        if (stored != null && stored.size() >= 2) {
            return stored;
        }
        return PolylineGeometry.straightLine(
                geoPointOf(network.getIntersection(road.getStartIntersectionId())),
                geoPointOf(network.getIntersection(road.getEndIntersectionId())));
    }

    private GeoPoint geoPointOf(Intersection intersection) {
        if (intersection == null
                || intersection.getLatitude() == null
                || intersection.getLongitude() == null) {
            return null;
        }
        return new GeoPoint(intersection.getLatitude(), intersection.getLongitude());
    }
}
