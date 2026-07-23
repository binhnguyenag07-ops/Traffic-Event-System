package team.traffic.domain.vehicle.pose;

import java.util.List;
import java.util.Optional;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Vehicle;

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
        position = PolylineGeometry.offsetMetres(position, heading, lateralOffset);
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

    private Optional<VehiclePose> poseInJunction(Vehicle vehicle, RoadNetwork network) {
        Intersection junction = network.getIntersection(vehicle.getCurrentIntersectionId());
        GeoPoint position = geoPointOf(junction);
        if (position == null) {
            return Optional.empty();
        }
        return Optional.of(new VehiclePose(
                position,
                headingAtJunction(vehicle, network),
                vehicle.getLength(),
                vehicle.getWidth(),
                null,
                0,
                0.0,
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
