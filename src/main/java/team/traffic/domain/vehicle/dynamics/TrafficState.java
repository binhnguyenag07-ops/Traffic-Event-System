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
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.signal.TrafficSignal;


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
    private final double elapsedSeconds;
    private final RoadNetwork network;
    private final List<TrafficParticipant> vehicles;
    private final RightOfWayPolicy rightOfWayPolicy;
    private final GapAcceptanceModel gapAcceptanceModel;
    private final RoadLayout roadLayout;
    private final List<TrafficEvent> events;
    private final Map<String, TrafficSignal> signalsByJunctionId;
    private final Map<String, RoadOccupancy> occupancyCache = new HashMap<>();
    private final Map<String, JunctionController> controllers = new LinkedHashMap<>();
    private final Set<String> clearedVehicleIds = new HashSet<>();
    private final Map<String, JunctionDecision> junctionDecisions = new LinkedHashMap<>();
    private JunctionRecoveryControl recoveryControl = JunctionRecoveryControl.NONE;
    private Map<String, List<TrafficParticipant>> vehiclesByRoadId;

    private TrafficState(
            long tick,
            double elapsedSeconds,
            RoadNetwork network,
            List<TrafficParticipant> vehicles,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel,
            RoadLayout roadLayout,
            List<TrafficEvent> events,
            List<TrafficSignal> signals) {
        this.tick = tick;
        this.elapsedSeconds = elapsedSeconds;
        this.network = network;
        this.vehicles = vehicles;
        this.rightOfWayPolicy = rightOfWayPolicy;
        this.gapAcceptanceModel = gapAcceptanceModel;
        this.roadLayout = roadLayout;
        this.events = events;
        Map<String, TrafficSignal> indexedSignals = new HashMap<>();
        for (TrafficSignal signal : signals) indexedSignals.put(signal.getJunctionId(), signal);
        this.signalsByJunctionId = Map.copyOf(indexedSignals);
    }

    public static TrafficState capture(long tick, RoadNetwork network, List<? extends TrafficParticipant> vehicles) {
        return capture(tick, tick, network, vehicles, List.of(),
                new PriorityRightOfWayPolicy(), new GapAcceptanceModel(), new RoadDataRoadLayout());
    }

    public static TrafficState capture(
            long tick,
            RoadNetwork network,
            List<? extends TrafficParticipant> vehicles,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel) {
        return capture(tick, tick, network, vehicles, List.of(),
                rightOfWayPolicy, gapAcceptanceModel, new RoadDataRoadLayout());
    }

    public static TrafficState capture(
            long tick,
            double elapsedSeconds,
            RoadNetwork network,
            List<? extends TrafficParticipant> vehicles,
            List<TrafficEvent> events,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel,
            RoadLayout roadLayout) {
        return capture(tick, elapsedSeconds, network, vehicles, events, List.of(),
                rightOfWayPolicy, gapAcceptanceModel, roadLayout);
    }

    public static TrafficState capture(
            long tick,
            double elapsedSeconds,
            RoadNetwork network,
            List<? extends TrafficParticipant> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel,
            RoadLayout roadLayout) {
        if (tick < 0) {
            throw new IllegalArgumentException("Tick must not be negative.");
        }
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must not be negative.");
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
        if (roadLayout == null) {
            throw new IllegalArgumentException("Road layout is required.");
        }
        return new TrafficState(
                tick,
                elapsedSeconds,
                network,
                vehicles == null ? List.of() : List.copyOf(vehicles),
                rightOfWayPolicy,
                gapAcceptanceModel,
                roadLayout,
                events == null ? List.of() : List.copyOf(events),
                signals == null ? List.of() : List.copyOf(signals));
    }

    public long getTick() {
        return tick;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public RoadNetwork getNetwork() {
        return network;
    }

    public List<TrafficEvent> getEvents() {
        return events;
    }

    public java.util.Optional<TrafficSignal> signalAt(String junctionId) {
        return java.util.Optional.ofNullable(signalsByJunctionId.get(junctionId));
    }

    /** Physical occupancy of one road, or {@code null} for an unknown road. */
    public RoadOccupancy occupancyOf(String roadId) {
        if (roadId == null) {
            return null;
        }
        RoadOccupancy cached = occupancyCache.get(roadId);
        if (cached != null) {
            return cached;
        }
        Road road = network.getRoad(roadId);
        if (road == null) {
            return null;
        }
        RoadOccupancy occupancy = RoadOccupancy.of(
                road, vehiclesOn(roadId), roadLayout, events);
        occupancyCache.put(roadId, occupancy);
        return occupancy;
    }

    /** Ids of the roads that currently carry at least one vehicle. */
    public Set<String> getOccupiedRoadIds() {
        return Set.copyOf(vehiclesByRoadId().keySet());
    }

    private List<TrafficParticipant> vehiclesOn(String roadId) {
        return vehiclesByRoadId().getOrDefault(roadId, List.of());
    }

    /**
     * Groups vehicles by the road they occupy, built once and then reused.
     * Rebuilt from scratch whenever a vehicle enters or leaves a road, which
     * happens far less often than occupancy is read.
     */
    private Map<String, List<TrafficParticipant>> vehiclesByRoadId() {
        if (vehiclesByRoadId == null) {
            Map<String, List<TrafficParticipant>> index = new HashMap<>();
            for (TrafficParticipant vehicle : vehicles) {
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
        RoadOccupancy occupancy = occupancyOf(roadId);
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

    /**
     * Registers a vehicle waiting to cross. Call before {@link #resolveJunctions()}.
     *
     * <p>Only the physical head of a queue may reserve a junction. Followers
     * still receive ordinary leader/signal constraints while moving towards the
     * stop line, but cannot create a "ghost" reservation that blocks another
     * arm while they are unable to move themselves.</p>
     */
    public void requestCrossing(JunctionApproach approach) {
        if (approach == null) {
            throw new IllegalArgumentException("Approach is required.");
        }
        if (!isPhysicalQueueHead(approach)) {
            return;
        }
        controllers
                .computeIfAbsent(
                        approach.junctionId(),
                        id -> new JunctionController(id, rightOfWayPolicy, gapAcceptanceModel))
                .register(approach);
    }

    private boolean isPhysicalQueueHead(JunctionApproach approach) {
        Road fromRoad = approach.fromRoad();
        String occupiedRoadId = approach.vehicle().getOccupiedRoadId();
        // Departures and the short junction-side state have no upstream lane.
        if (fromRoad == null || occupiedRoadId == null) {
            return true;
        }
        if (!fromRoad.getId().equals(occupiedRoadId)) {
            return false;
        }
        RoadOccupancy occupancy = occupancyOf(occupiedRoadId);
        // Use the same side clearance as longitudinal movement. With zero
        // clearance an ambulance 0.25 m beside a filtering motorcycle looked
        // like an independent queue head here, then saw that motorcycle as a
        // leader while moving. Its dispatch priority could reserve the shared
        // exit forever even though it was physically unable to use it.
        return occupancy != null
                && occupancy.findLeaderFor(
                        approach.vehicle(),
                        Lane.QUEUE_LATERAL_CLEARANCE_METRES).isFreeRoad();
    }

    /** Runs every junction's arbitration and records who may proceed. */
    public void resolveJunctions() {
        clearedVehicleIds.clear();
        junctionDecisions.clear();
        for (JunctionController controller : controllers.values()) {
            Map<String, JunctionDecision> decisions = controller.resolveDecisions(this);
            junctionDecisions.putAll(decisions);
            decisions.forEach((vehicleId, decision) -> {
                if (decision.granted()) {
                    clearedVehicleIds.add(vehicleId);
                }
            });
        }
    }

    public void applyRecoveryControl(JunctionRecoveryControl recoveryControl) {
        this.recoveryControl = recoveryControl == null
                ? JunctionRecoveryControl.NONE
                : recoveryControl;
    }

    public boolean isCleared(String vehicleId) {
        return vehicleId != null && clearedVehicleIds.contains(vehicleId);
    }

    public Set<String> getClearedVehicleIds() {
        return Set.copyOf(clearedVehicleIds);
    }

    public java.util.Optional<JunctionDecision> junctionDecisionFor(String vehicleId) {
        return java.util.Optional.ofNullable(junctionDecisions.get(vehicleId));
    }

    public Map<String, JunctionDecision> getJunctionDecisions() {
        return Map.copyOf(junctionDecisions);
    }

    @Override
    public boolean hasPhysicalSpaceFor(JunctionApproach approach) {
        RoadOccupancy target = occupancyOf(approach.toRoad().getId());
        return target != null
                && target.canAdmitFromLane(
                        approach.vehicle(), approach.fromLaneIndex());
    }

    @Override
    public double secondsUntilNextArrival(String junctionId, String excludedIncomingRoadId) {
        double soonest = Double.POSITIVE_INFINITY;
        for (Road incoming : network.findIncomingRoads(junctionId)) {
            if (incoming.getId().equals(excludedIncomingRoadId)) {
                continue;
            }
            RoadOccupancy occupancy = occupancyOf(incoming.getId());
            if (occupancy == null) {
                continue;
            }
            for (TrafficParticipant vehicle : occupancy.getVehicles()) {
                double speedMps = vehicle.getInstantaneousSpeedMps();
                if (speedMps < MOVING_THRESHOLD_MPS) {
                    // Standing traffic is not about to cross the junction.
                    continue;
                }
                Lane lane = occupancy.laneOf(vehicle);
                if (lane != null) {
                    soonest = Math.min(soonest, lane.distanceToExitMetres(vehicle) / speedMps);
                }
            }
        }
        return soonest;
    }

    @Override
    public boolean isSignalGreen(JunctionApproach approach) {
        TrafficSignal signal = signalsByJunctionId.get(approach.junctionId());
        return signal == null || approach.fromRoad() == null
                || signal.isGreenFor(approach.fromRoad().getId(), elapsedSeconds);
    }

    @Override
    public java.util.OptionalDouble expectedSignalReleaseAt(
            JunctionApproach approach) {
        TrafficSignal signal = signalsByJunctionId.get(approach.junctionId());
        if (signal == null || isSignalGreen(approach)) {
            return java.util.OptionalDouble.empty();
        }
        double untilGreen = signal.secondsUntilGreenFor(
                approach.fromRoad() == null
                        ? null
                        : approach.fromRoad().getId(),
                elapsedSeconds);
        return Double.isFinite(untilGreen)
                ? java.util.OptionalDouble.of(elapsedSeconds + untilGreen)
                : java.util.OptionalDouble.empty();
    }

    @Override
    public boolean isRecoveryHeld(JunctionApproach approach) {
        return recoveryControl.isHeld(approach);
    }

    @Override
    public boolean isRecoveryReserved(JunctionApproach approach) {
        return recoveryControl.isReserved(approach);
    }
}
