package team.traffic.domain.vehicle;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoutingService;

public class Bus extends Vehicle {
    private static final double DEFAULT_CRUISING_SPEED_KPH = 35.0;
    private final int passengerCapacity;
    private java.util.List<BusStop> stops = java.util.List.of();
    private final java.util.Set<String> servedStopJunctionIds = new java.util.LinkedHashSet<>();
    private String dwellingAtJunctionId;
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

    public java.util.List<BusStop> getStops() {
        return stops;
    }

    public void setStops(java.util.List<BusStop> stops) {
        this.stops = stops == null ? java.util.List.of() : java.util.List.copyOf(stops);
        servedStopJunctionIds.clear();
        dwellingAtJunctionId = null;
        dwellUntilSeconds = 0.0;
    }

    /** Rebuilds the default stop list after creation or a dynamic reroute. */
    public void configureDefaultStopsFromRoute() {
        if (getCurrentRoute() == null || getCurrentRoute().getStepCount() < 2) {
            setStops(java.util.List.of());
            return;
        }
        java.util.LinkedHashSet<String> intermediateJunctions = new java.util.LinkedHashSet<>();
        int lastStep = getCurrentRoute().getStepCount() - 1;
        for (int index = 0; index < lastStep; index++) {
            intermediateJunctions.add(getCurrentRoute().getSteps().get(index).getToIntersectionId());
        }
        setStops(intermediateJunctions.stream()
                .map(junctionId -> BusStop.forPassengerCapacity(junctionId, passengerCapacity))
                .toList());
    }

    @Override
    public void recomputeRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        super.recomputeRoute(roadNetwork, routingService);
        configureDefaultStopsFromRoute();
    }

    public java.util.Set<String> getServedStopJunctionIds() {
        return java.util.Set.copyOf(servedStopJunctionIds);
    }

    @Override
    protected boolean shouldHoldAtRoadExit(
            String junctionId, double distanceToExitMetres, double elapsedSeconds) {
        BusStop stop = stops.stream()
                .filter(candidate -> candidate.junctionId().equals(junctionId))
                .filter(candidate -> !servedStopJunctionIds.contains(candidate.junctionId()))
                .findFirst()
                .orElse(null);
        if (stop == null) return false;
        // Announce the stop while approaching so the following model brakes at
        // the line, but begin passenger dwell only once the bus is at the curb.
        if (distanceToExitMetres > 1.0 && !junctionId.equals(dwellingAtJunctionId)) {
            return true;
        }
        if (!junctionId.equals(dwellingAtJunctionId)) {
            dwellingAtJunctionId = junctionId;
            dwellUntilSeconds = elapsedSeconds + stop.dwellSeconds();
        }
        if (elapsedSeconds < dwellUntilSeconds) return true;
        servedStopJunctionIds.add(junctionId);
        dwellingAtJunctionId = null;
        dwellUntilSeconds = 0.0;
        return false;
    }
}
