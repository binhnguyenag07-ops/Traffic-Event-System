package team.traffic.domain.vehicle.pose;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import team.traffic.domain.demand.PlaceKind;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.JourneyPhase;
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

        JourneyPhase phase = vehicle.getJourneyPhase();
        if (phase == JourneyPhase.DEPARTING_ORIGIN
                || phase == JourneyPhase.ARRIVING_DESTINATION) {
            return poseOnBuildingAccess(vehicle, network, phase);
        }
        if (isStationaryAtBuilding(vehicle, phase)) {
            return poseAtCurrentBuilding(vehicle, network);
        }

        Road occupiedRoad = vehicle.getOccupiedRoadId() == null
                ? null
                : network.getRoad(vehicle.getOccupiedRoadId());
        return occupiedRoad == null
                ? poseInJunction(vehicle, network)
                : poseOnRoad(vehicle, network, occupiedRoad);
    }

    /** Resolves the authoritative low-speed link between a building and its access node. */
    private Optional<VehiclePose> poseOnBuildingAccess(
            Vehicle vehicle,
            RoadNetwork network,
            JourneyPhase phase) {
        var buildingPlace = phase == JourneyPhase.DEPARTING_ORIGIN
                ? vehicle.getOriginResolvedPlace()
                : vehicle.getDestinationResolvedPlace();
        if (buildingPlace == null
                || buildingPlace.kind() != PlaceKind.BUILDING) {
            return Optional.empty();
        }
        GeoPoint buildingCentre = buildingCentreOf(buildingPlace, network);
        GeoPoint access = geoPointOf(
                network.getIntersection(buildingPlace.accessIntersectionId()));
        if (buildingCentre == null || access == null) {
            return Optional.empty();
        }

        GeoPoint from = phase == JourneyPhase.DEPARTING_ORIGIN
                ? buildingCentre : access;
        GeoPoint to = phase == JourneyPhase.DEPARTING_ORIGIN
                ? access : buildingCentre;
        List<GeoPoint> link = List.of(from, to);
        double progress = vehicle.getAccessLinkProgress();
        GeoPoint position = PolylineGeometry.pointAt(link, progress);
        if (position == null) return Optional.empty();
        return Optional.of(new VehiclePose(
                position,
                PolylineGeometry.bearingAt(link, progress),
                vehicle.getLength(),
                vehicle.getWidth(),
                null,
                0,
                0.0,
                false));
    }

    /**
     * A vehicle that has completed its arrival link stays at the building while
     * it dwells or waits for final-arrival retention. Falling through to the
     * junction queue would move it back to the stop line it already left.
     */
    private Optional<VehiclePose> poseAtCurrentBuilding(
            Vehicle vehicle,
            RoadNetwork network) {
        ResolvedPlace place = vehicle.getCurrentResolvedPlace();
        GeoPoint buildingCentre = buildingCentreOf(place, network);
        if (buildingCentre == null) {
            return Optional.empty();
        }
        GeoPoint access = geoPointOf(
                network.getIntersection(place.accessIntersectionId()));
        double heading = access != null
                && PolylineGeometry.lengthMetres(List.of(access, buildingCentre)) > 0.01
                ? PolylineGeometry.bearingAt(List.of(access, buildingCentre), 1.0)
                : headingAtJunction(vehicle, network);
        return Optional.of(new VehiclePose(
                buildingCentre,
                heading,
                vehicle.getLength(),
                vehicle.getWidth(),
                null,
                0,
                0.0,
                false));
    }

    private static boolean isStationaryAtBuilding(
            Vehicle vehicle,
            JourneyPhase phase) {
        ResolvedPlace current = vehicle.getCurrentResolvedPlace();
        return (phase == JourneyPhase.DWELLING || phase == JourneyPhase.ARRIVED)
                && current != null
                && current.kind() == PlaceKind.BUILDING;
    }

    private static GeoPoint buildingCentreOf(
            ResolvedPlace place,
            RoadNetwork network) {
        if (place == null || place.kind() != PlaceKind.BUILDING) {
            return null;
        }
        var building = network.getBuilding(place.id());
        return building == null
                || building.getLatitude() == null
                || building.getLongitude() == null
                ? null
                : new GeoPoint(building.getLatitude(), building.getLongitude());
    }

    private Optional<VehiclePose> poseOnRoad(Vehicle vehicle, RoadNetwork network, Road road) {
        List<GeoPoint> shape = shapeOf(road, network);
        // Progress is measured against the road as the network models it, node
        // to node. It is drawn against the stretch that is actually road, so
        // that entering a road puts the vehicle at the junction's far edge
        // rather than on its centre, and the turning curve it just followed
        // ends where the vehicle now is.
        double frontBumperFraction = new RoadExtent(network, roadLayout)
                .drivableFraction(road, vehicle.getRoadProgress());
        double fraction = centreFractionBehindFront(
                shape, frontBumperFraction, vehicle.getLength());
        GeoPoint position = PolylineGeometry.pointAt(shape, fraction);
        if (position == null) {
            return Optional.empty();
        }
        double heading = PolylineGeometry.bearingAt(shape, fraction);
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
     * <p>So a vehicle that has travelled is put at the exit of the road it
     * arrived on, which is where the stop line is. A newly spawned building or
     * boundary trip is instead put at its resolved off-network source; it does
     * not occupy tarmac until junction control admits it to the first road.</p>
     */
    private Optional<VehiclePose> poseInJunction(Vehicle vehicle, RoadNetwork network) {
        Road arrivedOn = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex() - 1);
        if (arrivedOn != null) {
            return approachPose(vehicle, network, arrivedOn, stopLineFraction(arrivedOn, network));
        }

        return departurePose(vehicle, network);
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
     * <p>Order within a queue is by how long each vehicle has been waiting, so
     * the one that arrived first stands at the front. Ordering by vehicle id
     * instead — which is all this used to have — is stable but arbitrary, and it
     * showed: a car arriving at a junction would be drawn ahead of one that had
     * been sitting there for a minute, and the two appeared to swap places. Ties
     * fall back to the id, so the same captured state always draws the same
     * way.</p>
     */
    public Map<String, VehiclePose> resolveAll(List<Vehicle> vehicles, RoadNetwork network) {
        Map<String, VehiclePose> poses = new LinkedHashMap<>();
        if (vehicles == null || network == null) {
            return poses;
        }
        Map<String, Double> queuedMetres = new HashMap<>();
        for (Vehicle vehicle : vehicles.stream()
                .sorted(Comparator
                        .comparingDouble((Vehicle queued) -> queued.getWaitState().sinceSeconds())
                        .thenComparing(Vehicle::getId))
                .toList()) {
            Approach approach = approachOf(vehicle, network);
            if (approach == null) {
                resolve(vehicle, network).ifPresent(pose -> poses.put(vehicle.getId(), pose));
                continue;
            }
            String slot = approach.road().getId() + "|" + vehicle.getLastLaneIndex()
                    + "|" + approach.fraction();
            double alreadyQueued = queuedMetres.getOrDefault(slot, 0.0);
            approachPose(vehicle, network, approach.road(), approach.fraction(), alreadyQueued)
                    .ifPresent(pose -> poses.put(vehicle.getId(), pose));
            queuedMetres.put(slot, alreadyQueued + vehicle.getLength() + QUEUE_GAP_METRES);
        }
        return poses;
    }

    /**
     * Which road end a road-less vehicle belongs to, or null if it holds a road.
     *
     * <p>Only a vehicle that has already travelled gets one. A vehicle that has
     * not moved off yet is deliberately left without a pose — see
     * {@link #poseInJunction}.</p>
     */
    private Approach approachOf(Vehicle vehicle, RoadNetwork network) {
        JourneyPhase phase = vehicle.getJourneyPhase();
        if (phase == JourneyPhase.DEPARTING_ORIGIN
                || phase == JourneyPhase.ARRIVING_DESTINATION
                || isStationaryAtBuilding(vehicle, phase)) {
            return null;
        }
        if (vehicle.getOccupiedRoadId() != null
                && network.getRoad(vehicle.getOccupiedRoadId()) != null) {
            return null;
        }
        Road arrivedOn = routeRoadAt(vehicle, network, vehicle.getNextRouteStepIndex() - 1);
        return arrivedOn == null
                ? null
                : new Approach(arrivedOn, stopLineFraction(arrivedOn, network));
    }

    private record Approach(Road road, double fraction) {}

    /** Where along a road its stop line sits, as a fraction of its whole shape. */
    private double stopLineFraction(Road road, RoadNetwork network) {
        return new RoadExtent(network, roadLayout).exitFraction(road);
    }

    private Optional<VehiclePose> approachPose(
            Vehicle vehicle, RoadNetwork network, Road road, double fraction) {
        return approachPose(vehicle, network, road, fraction, 0.0);
    }

    /**
     * The vehicle placed on one end of a road, in its own lane and carriageway,
     * held back {@code queuedMetres} along the road by the vehicles ahead of it.
     *
     * <p>The queue extends backwards from the stop line, which is where a queue
     * waiting to cross actually forms.</p>
     */
    private Optional<VehiclePose> approachPose(
            Vehicle vehicle, RoadNetwork network, Road road, double fraction, double queuedMetres) {
        List<GeoPoint> shape = shapeOf(road, network);
        double lengthMetres = PolylineGeometry.lengthMetres(shape);
        if (lengthMetres > 0) {
            // Longitudinal progress is the front-bumper position throughout the
            // microscopic model (leader gaps subtract the leader's full
            // length). A pose is the vehicle centre, so the first vehicle's
            // centre must be half a body behind the stop line. Without this
            // conversion every sprite visibly overran a red light by half its
            // length even though its authoritative front bumper had stopped.
            double behindFrontMetres = queuedMetres + vehicle.getLength() / 2.0;
            fraction = Math.max(
                    0.0,
                    fraction - Math.min(behindFrontMetres / lengthMetres, 1.0));
        }
        GeoPoint position = PolylineGeometry.pointAt(shape, fraction);
        if (position == null) {
            return Optional.empty();
        }
        double heading = PolylineGeometry.bearingAt(shape, fraction);
        // The lane it drove up in, not the zero its placement was reset to when
        // it gave up the road: a queue belongs in the lane that formed it.
        int laneIndex = vehicle.getLastLaneIndex();
        double lateralOffset = roadLayout.laneCentreOffsetMetresOf(road, laneIndex);
        double carriageway = Carriageway.offsetMetres(network, road, roadLayout);
        return Optional.of(new VehiclePose(
                PolylineGeometry.offsetMetres(position, heading, lateralOffset + carriageway),
                heading,
                vehicle.getLength(),
                vehicle.getWidth(),
                null,
                laneIndex,
                lateralOffset,
                true));
    }

    /**
     * Gives an off-network demand source a real first pose.
     *
     * <p>A building-origin vehicle starts at the building centre and a boundary
     * vehicle starts at its boundary access node. Neither pose occupies a road,
     * so it remains absent from lane occupancy and cannot block traffic. The
     * journey phase and resolved access id published with the frame let a
     * renderer animate the short driveway/boundary-entry segment before the
     * first authoritative road pose.</p>
     */
    private Optional<VehiclePose> departurePose(Vehicle vehicle, RoadNetwork network) {
        var origin = vehicle.getOriginResolvedPlace();
        if (origin == null || vehicle.getNextRouteStepIndex() != 0) {
            return Optional.empty();
        }

        GeoPoint access = geoPointOf(network.getIntersection(origin.accessIntersectionId()));
        GeoPoint position = switch (origin.kind()) {
            case BUILDING -> vehicle.getAccessLinkProgress() >= 1.0 - 1e-9
                    ? access
                    : buildingCentreOf(origin, network);
            case BOUNDARY -> geoPointOf(network.getIntersection(origin.accessIntersectionId()));
            default -> null;
        };
        if (position == null) {
            return Optional.empty();
        }

        double heading = access != null
                && PolylineGeometry.lengthMetres(List.of(position, access)) > 0.01
                ? PolylineGeometry.bearingAt(List.of(position, access), 0.0)
                : headingAtJunction(vehicle, network);
        return Optional.of(new VehiclePose(
                position,
                heading,
                vehicle.getLength(),
                vehicle.getWidth(),
                null,
                0,
                0.0,
                false));
    }

    /** Converts a front-bumper fraction to the centre point a sprite represents. */
    private static double centreFractionBehindFront(
            List<GeoPoint> shape,
            double frontBumperFraction,
            double vehicleLengthMetres) {
        double lengthMetres = PolylineGeometry.lengthMetres(shape);
        if (lengthMetres <= 0.0) {
            return frontBumperFraction;
        }
        return Math.max(
                0.0,
                frontBumperFraction - Math.min(
                        vehicleLengthMetres / (2.0 * lengthMetres), 1.0));
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
