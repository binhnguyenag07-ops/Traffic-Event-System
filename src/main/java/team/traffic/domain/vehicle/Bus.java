package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import team.traffic.domain.network.BusStop;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.service.RoutingService;

public class Bus extends Vehicle {
    private static final double DEFAULT_CRUISING_SPEED_KPH = 35.0;

    /**
     * How close to the kerb counts as arrived, in metres.
     *
     * <p>A bus never lands exactly on a stop: it moves in sub-steps of up to a
     * second and overshoots or stops short by whatever it covered in the last
     * one. Without a tolerance it would creep towards a target it can never hit
     * and dwell would never begin.</p>
     */
    private static final double ARRIVAL_TOLERANCE_METRES = 2.0;

    private final int passengerCapacity;
    private RoadNetwork network;
    private Route stopsBuiltForRoute;
    private List<BusStop> stops = List.of();
    private final Set<String> servedStopIds = new LinkedHashSet<>();
    private String dwellingAtStopId;
    private double dwellUntilSeconds;

    public Bus(String id, String currentIntersectionId, String destinationIntersectionId, int passengerCapacity) {
        this(id, currentIntersectionId, destinationIntersectionId, passengerCapacity,
                VehicleProfile.bus(), DEFAULT_CRUISING_SPEED_KPH);
    }

    public Bus(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            int passengerCapacity,
            VehicleProfile profile,
            double cruisingSpeedKph) {
        super(id, currentIntersectionId, destinationIntersectionId, profile, cruisingSpeedKph);
        if (passengerCapacity <= 0) {
            throw new IllegalArgumentException("Passenger capacity must be positive.");
        }
        this.passengerCapacity = passengerCapacity;
    }

    @Override
    public VehicleType getType() {
        return VehicleType.BUS;
    }

    public int getPassengerCapacity() {
        return passengerCapacity;
    }

    /** Stops this bus will serve, in the order its route passes them. */
    public List<BusStop> getStops() {
        return stops;
    }

    public void setStops(List<BusStop> stops) {
        this.stops = stops == null ? List.of() : List.copyOf(stops);
        servedStopIds.clear();
        dwellingAtStopId = null;
        dwellUntilSeconds = 0.0;
    }

    public Set<String> getServedStopIds() {
        return Set.copyOf(servedStopIds);
    }

    /**
     * Collects the kerb-side stops lying on this bus's current route.
     *
     * <p>Dwell comes from this bus's capacity rather than from the map: boarding
     * a sixty-seat bus takes longer than boarding a minibus at the same kerb, so
     * the wait belongs to the vehicle and not to the concrete.</p>
     */
    public void configureStopsFromNetwork(RoadNetwork roadNetwork) {
        this.network = roadNetwork;
        refreshStopsIfRouteChanged();
    }

    /**
     * Rebuilds the stop list when the route it was derived from is no longer
     * the current one.
     *
     * <p>Callers configure a bus at whatever moment suits them, and for a
     * demand-spawned bus that moment is before the trip lifecycle has assigned
     * it a route at all -- the stop list would be permanently empty and the bus
     * would sail past every kerb. Rechecking here rather than trusting call
     * order also covers a multi-leg plan, where each leg brings a new route and
     * therefore a new set of stops.</p>
     */
    private void refreshStopsIfRouteChanged() {
        Route route = getCurrentRoute();
        if (route == stopsBuiltForRoute) return;
        stopsBuiltForRoute = route;
        if (network == null || route == null) {
            setStops(List.of());
            return;
        }
        List<BusStop> onRoute = new ArrayList<>();
        for (var step : route.getSteps()) {
            for (BusStop stop : network.getBusStopsOnRoad(step.getRoadId())) {
                onRoute.add(new BusStop(
                        stop.id(),
                        stop.name(),
                        stop.roadId(),
                        stop.positionAlongRoad(),
                        BusStop.dwellForPassengerCapacity(passengerCapacity)));
            }
        }
        setStops(onRoute);
    }

    @Override
    public void recomputeRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        super.recomputeRoute(roadNetwork, routingService);
        configureStopsFromNetwork(roadNetwork);
    }

    /**
     * Distance to the next kerb this bus owes a stop to, or infinity.
     *
     * <p>Returning a distance rather than a boolean is what lets a stop sit
     * anywhere along the road. The car-following model already knows how to
     * brake smoothly to a point ahead, so a bus decelerates into its stop the
     * same way it decelerates for a queue, and the traffic behind reacts to the
     * bus itself rather than to a special case.</p>
     */
    @Override
    protected double holdDistanceAheadMetres(Road road, double progressAlongRoad, double elapsedSeconds) {
        refreshStopsIfRouteChanged();
        if (road == null || stops.isEmpty()) return Double.POSITIVE_INFINITY;

        BusStop next = null;
        for (BusStop stop : stops) {
            if (!stop.roadId().equals(road.getId())) continue;
            if (servedStopIds.contains(stop.id())) continue;
            // Keep a stop selected while dwelling at it even though the bus may
            // have crept a few centimetres past its nominal position.
            if (stop.id().equals(dwellingAtStopId)) {
                next = stop;
                break;
            }
            if (stop.positionAlongRoad() >= progressAlongRoad) {
                next = stop;
                break;
            }
        }
        if (next == null) return Double.POSITIVE_INFINITY;

        double distanceMetres =
                (next.positionAlongRoad() - progressAlongRoad) * road.getDistance() * 1_000.0;

        if (next.id().equals(dwellingAtStopId)) {
            if (elapsedSeconds < dwellUntilSeconds) return 0.0;
            servedStopIds.add(next.id());
            dwellingAtStopId = null;
            dwellUntilSeconds = 0.0;
            return Double.POSITIVE_INFINITY;
        }
        if (distanceMetres <= ARRIVAL_TOLERANCE_METRES) {
            dwellingAtStopId = next.id();
            dwellUntilSeconds = elapsedSeconds + next.dwellSeconds();
            return 0.0;
        }
        return distanceMetres;
    }
}
