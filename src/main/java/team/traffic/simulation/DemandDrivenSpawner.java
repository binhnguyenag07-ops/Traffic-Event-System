package team.traffic.simulation;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.demand.TripDemandModel;
import team.traffic.domain.demand.TripPlan;
import team.traffic.domain.demand.TripPlanReachability;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.service.RoutingService;

/** Turns due resolved plans into routed vehicles without knowing how demand was generated. */
public final class DemandDrivenSpawner {
    private static final double INITIAL_CURSOR_SECONDS = -1e-9;

    private final TripDemandModel demandModel;
    private final RoadNetwork network;
    private final RoutingService routingService;
    private final VehicleFactory vehicleFactory;
    private final List<String> failedPlanIds = new ArrayList<>();
    private double scannedThroughSeconds = INITIAL_CURSOR_SECONDS;
    private int spawnedTrips;

    public DemandDrivenSpawner(
            TripDemandModel demandModel,
            RoadNetwork network,
            RoutingService routingService) {
        this(demandModel, network, routingService, new VehicleFactory());
    }

    public DemandDrivenSpawner(
            TripDemandModel demandModel,
            RoadNetwork network,
            RoutingService routingService,
            VehicleFactory vehicleFactory) {
        if (demandModel == null) throw new IllegalArgumentException("Trip demand model is required");
        if (network == null) throw new IllegalArgumentException("Road network is required");
        if (routingService == null) throw new IllegalArgumentException("Routing service is required");
        this.demandModel = demandModel;
        this.network = network;
        this.routingService = routingService;
        if (vehicleFactory == null) throw new IllegalArgumentException("Vehicle factory is required");
        this.vehicleFactory = vehicleFactory;
        this.failedPlanIds.addAll(demandModel.generationFailedPlanIds());
    }

    public List<Vehicle> spawnDueThrough(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must be finite and non-negative");
        }
        if (elapsedSeconds < scannedThroughSeconds) {
            throw new IllegalArgumentException("Demand spawner time must not move backwards");
        }
        List<TripPlan> due = demandModel.tripsBeginningIn(scannedThroughSeconds, elapsedSeconds);
        scannedThroughSeconds = elapsedSeconds;
        List<Vehicle> spawned = new ArrayList<>();
        for (TripPlan plan : due) {
            try {
                Vehicle vehicle = createVehicleForPlan(plan);
                // A structurally valid plan can still have no route at this
                // instant because its entry road is full or a live event has
                // closed the preferred path. That is a traffic queue, not bad
                // demand. Keep the BLOCKED vehicle in the runtime; the normal
                // tick retry logic will admit/reroute it when capacity returns.
                spawned.add(vehicle);
                spawnedTrips++;
            } catch (RuntimeException exception) {
                failedPlanIds.add(plan.id());
            }
        }
        return List.copyOf(spawned);
    }

    public Vehicle createVehicleForPlan(TripPlan plan) {
        if (plan == null) throw new IllegalArgumentException("Trip plan is required");
        requireStructurallyReachable(plan);
        Vehicle vehicle = vehicleFactory.createVehicle(
                plan.vehicleType(),
                plan.id(),
                plan.origin().accessIntersectionId(),
                plan.legs().get(0).to().accessIntersectionId());
        vehicle.assignTripPlan(plan);
        if (!vehicle.hasReachedDestination()) {
            vehicle.requestRoute(network, routingService);
        }
        return vehicle;
    }

    /**
     * Rejects a broken resolved plan before its vehicle enters the simulation.
     * This checks the directed topology only; live closures remain the routing
     * layer's responsibility and can still be retried later.
     */
    private void requireStructurallyReachable(TripPlan plan) {
        TripPlanReachability.firstUnreachableLeg(network, plan).ifPresent(leg -> {
            throw new IllegalArgumentException(
                    "Trip plan " + plan.id() + " has no directed path from "
                            + leg.fromIntersectionId() + " to " + leg.toIntersectionId());
        });
    }

    public DemandRunStatistics statistics() {
        int scheduled = demandModel.scheduledTripCount();
        int failed = failedPlanIds.size();
        return new DemandRunStatistics(
                scheduled,
                spawnedTrips,
                failed,
                Math.max(0, scheduled - spawnedTrips - failed),
                failedPlanIds);
    }
}
