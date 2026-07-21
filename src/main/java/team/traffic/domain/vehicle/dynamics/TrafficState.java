package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;

/**
 * The working state of one simulation step: where every vehicle physically is,
 * how full each road actually is, and who has been cleared to cross which
 * junction.
 *
 * <p>A fresh instance is built at the start of every step. Vehicles read it to
 * find their leader and to learn whether they may proceed, so each vehicle sees
 * the same picture of the network no matter where it sits in the vehicle list.</p>
 *
 * <p>Occupancy is computed on demand and dropped again whenever a vehicle
 * enters a road part-way through the step, so a vehicle that moves later in the
 * step still sees the space taken by one that moved earlier.</p>
 */
public final class TrafficState implements JunctionEnvironment {

    /** Below this speed a vehicle counts as standing rather than approaching. */
    private static final double MOVING_THRESHOLD_MPS = 0.1;

    private final long tick;
    private final RoadNetwork network;
    private final List<Vehicle> vehicles;
    private final RightOfWayPolicy rightOfWayPolicy;
    private final GapAcceptanceModel gapAcceptanceModel;
    private final Map<String, LaneOccupancy> occupancyCache = new HashMap<>();
    private final Map<String, JunctionController> controllers = new LinkedHashMap<>();
    private final Set<String> clearedVehicleIds = new HashSet<>();
    private Map<String, List<Vehicle>> vehiclesByRoadId;

    private TrafficState(
            long tick,
            RoadNetwork network,
            List<Vehicle> vehicles,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel) {
        this.tick = tick;
        this.network = network;
        this.vehicles = vehicles;
        this.rightOfWayPolicy = rightOfWayPolicy;
        this.gapAcceptanceModel = gapAcceptanceModel;
    }

    public static TrafficState capture(long tick, RoadNetwork network, List<Vehicle> vehicles) {
        return capture(tick, network, vehicles, new PriorityRightOfWayPolicy(), new GapAcceptanceModel());
    }

    public static TrafficState capture(
            long tick,
            RoadNetwork network,
            List<Vehicle> vehicles,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel) {
        if (tick < 0) {
            throw new IllegalArgumentException("Tick must not be negative.");
        }
        if (network == null) {
            throw new IllegalArgumentException("Road network is required.");
        }
        if (rightOfWayPolicy == null) {
            throw new IllegalArgumentException("Right-of-way policy is required.");
        }
        if (gapAcceptanceModel == null) {
            throw new IllegalArgumentException("Gap-acceptance model is required.");
        }
        return new TrafficState(
                tick,
                network,
                vehicles == null ? List.of() : new ArrayList<>(vehicles),
                rightOfWayPolicy,
                gapAcceptanceModel);
    }

    public long getTick() {
        return tick;
    }

    public RoadNetwork getNetwork() {
        return network;
    }

    /** Physical occupancy of one road, or {@code null} for an unknown road. */
    public LaneOccupancy occupancyOf(String roadId) {
        if (roadId == null) {
            return null;
        }
        LaneOccupancy cached = occupancyCache.get(roadId);
        if (cached != null) {
            return cached;
        }
        Road road = network.getRoad(roadId);
        if (road == null) {
            return null;
        }
        LaneOccupancy occupancy =
                LaneOccupancy.ofVehiclesAlreadyOnRoad(road, vehiclesOn(roadId));
        occupancyCache.put(roadId, occupancy);
        return occupancy;
    }

    /** Ids of the roads that currently carry at least one vehicle. */
    public Set<String> getOccupiedRoadIds() {
        return Set.copyOf(vehiclesByRoadId().keySet());
    }

    private List<Vehicle> vehiclesOn(String roadId) {
        return vehiclesByRoadId().getOrDefault(roadId, List.of());
    }

    /**
     * Groups vehicles by the road they occupy, built once and then reused.
     * Rebuilt from scratch whenever a vehicle enters or leaves a road, which
     * happens far less often than occupancy is read.
     */
    private Map<String, List<Vehicle>> vehiclesByRoadId() {
        if (vehiclesByRoadId == null) {
            Map<String, List<Vehicle>> index = new HashMap<>();
            for (Vehicle vehicle : vehicles) {
                String roadId = vehicle.getOccupiedRoadId();
                if (roadId != null) {
                    index.computeIfAbsent(roadId, id -> new ArrayList<>()).add(vehicle);
                }
            }
            vehiclesByRoadId = index;
        }
        return vehiclesByRoadId;
    }

    /**
     * Share of a road's physical length that is taken up, counting vehicle
     * bodies and standstill gaps. This is the size-aware counterpart to the
     * road's own count-based capacity usage.
     */
    public double physicalJamRatioOf(String roadId) {
        LaneOccupancy occupancy = occupancyOf(roadId);
        return occupancy == null ? 0.0 : occupancy.getPhysicalJamRatio();
    }

    /** Forgets the cached occupancy of a road after a vehicle enters or leaves it. */
    public void invalidateOccupancy(String roadId) {
        if (roadId != null) {
            occupancyCache.remove(roadId);
            vehiclesByRoadId = null;
        }
    }

    // --- junction arbitration ---

    /** Registers a vehicle waiting to cross. Call before {@link #resolveJunctions()}. */
    public void requestCrossing(JunctionApproach approach) {
        if (approach == null) {
            throw new IllegalArgumentException("Approach is required.");
        }
        controllers
                .computeIfAbsent(
                        approach.junctionId(),
                        id -> new JunctionController(id, rightOfWayPolicy, gapAcceptanceModel))
                .register(approach);
    }

    /** Runs every junction's arbitration and records who may proceed. */
    public void resolveJunctions() {
        clearedVehicleIds.clear();
        for (JunctionController controller : controllers.values()) {
            clearedVehicleIds.addAll(controller.resolve(this));
        }
    }

    public boolean isCleared(String vehicleId) {
        return vehicleId != null && clearedVehicleIds.contains(vehicleId);
    }

    public Set<String> getClearedVehicleIds() {
        return Set.copyOf(clearedVehicleIds);
    }

    @Override
    public boolean hasPhysicalSpaceFor(JunctionApproach approach) {
        LaneOccupancy target = occupancyOf(approach.toRoad().getId());
        return target != null && target.canAdmit(approach.vehicle());
    }

    @Override
    public double secondsUntilNextArrival(String junctionId, String excludedIncomingRoadId) {
        double soonest = Double.POSITIVE_INFINITY;
        for (Road incoming : network.findIncomingRoads(junctionId)) {
            if (incoming.getId().equals(excludedIncomingRoadId)) {
                continue;
            }
            LaneOccupancy occupancy = occupancyOf(incoming.getId());
            if (occupancy == null) {
                continue;
            }
            for (Vehicle vehicle : occupancy.getVehicles()) {
                double speedMps = vehicle.getInstantaneousSpeedMps();
                if (speedMps < MOVING_THRESHOLD_MPS) {
                    // Standing traffic is not about to cross the junction.
                    continue;
                }
                soonest = Math.min(soonest, occupancy.distanceToExitMetres(vehicle) / speedMps);
            }
        }
        return soonest;
    }
}
