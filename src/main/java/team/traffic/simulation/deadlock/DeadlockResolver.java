package team.traffic.simulation.deadlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.JunctionApproach;
import team.traffic.domain.vehicle.dynamics.RoadOccupancy;
import team.traffic.domain.vehicle.dynamics.TrafficParticipant;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/**
 * Strict deterministic recovery planner. It only selects a real legal
 * junction movement whose target lane can admit the vehicle now.
 */
public final class DeadlockResolver {
    private final double reservationSeconds;

    public DeadlockResolver(double reservationSeconds) {
        if (!Double.isFinite(reservationSeconds) || reservationSeconds <= 0) {
            throw new IllegalArgumentException("Reservation duration must be positive.");
        }
        this.reservationSeconds = reservationSeconds;
    }

    public Resolution resolve(
            DeadlockCycle cycle,
            double simulationSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            TrafficState state) {
        if (cycle == null || network == null || state == null) {
            throw new IllegalArgumentException("Cycle, network, and traffic state are required.");
        }
        MapView view = new MapView(vehicles);
        Set<String> regionRoadIds = cycle.vehicleIds().stream()
                .map(view::vehicle)
                .filter(java.util.Objects::nonNull)
                .map(Vehicle::getOccupiedRoadId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));

        List<Candidate> candidates = new ArrayList<>();
        for (String vehicleId : new TreeSet<>(cycle.vehicleIds())) {
            Vehicle vehicle = view.vehicle(vehicleId);
            if (vehicle == null) continue;
            Optional<JunctionApproach> planned =
                    vehicle.previewJunctionApproach(network, state);
            if (planned.isEmpty()) continue;
            JunctionApproach approach = planned.get();
            Optional<JunctionMovement> movement = network.getMovementGraph()
                    .find(
                            approach.junctionId(),
                            approach.fromRoad() == null
                                    ? null
                                    : approach.fromRoad().getId(),
                            approach.toRoad().getId())
                    .filter(value -> value.allows(vehicle.getType()));
            if (movement.isEmpty()) continue;
            RoadOccupancy target = state.occupancyOf(approach.toRoad().getId());
            int targetLane = target == null ? -1 : target.bestLaneForEntry(vehicle);
            if (targetLane < 0) continue;
            boolean leavesRegion = !regionRoadIds.contains(approach.toRoad().getId());
            candidates.add(new Candidate(
                    vehicle,
                    movement.get(),
                    targetLane,
                    leavesRegion,
                    Math.max(0.0, simulationSeconds - vehicle.getWaitState().sinceSeconds()),
                    movement.get().penaltySeconds(),
                    occupantIds(target.lane(targetLane))));
        }

        candidates.sort(Comparator
                .comparing(Candidate::leavesRegion).reversed()
                .thenComparing(
                        candidate -> candidate.vehicle().getPriorityLevel(),
                        Comparator.reverseOrder())
                .thenComparing(Candidate::waitedSeconds, Comparator.reverseOrder())
                .thenComparingDouble(Candidate::disruptionCost)
                .thenComparing(candidate -> candidate.vehicle().getId()));

        RegionGate gate = new RegionGate(
                cycle.fingerprint(),
                regionRoadIds,
                cycle.vehicleIds(),
                simulationSeconds,
                simulationSeconds + reservationSeconds);
        if (candidates.isEmpty()) {
            return new Resolution(
                    DeadlockStatus.UNRESOLVABLE_STRICT,
                    new RecoveryDirective(gate, Optional.empty()),
                    "NO_FREE_STORAGE_OR_LEGAL_EXIT");
        }

        Candidate selected = candidates.get(0);
        RecoveryReservation reservation = new RecoveryReservation(
                selected.vehicle().getId(),
                selected.movement(),
                selected.targetLaneIndex(),
                cycle.fingerprint(),
                simulationSeconds,
                simulationSeconds + reservationSeconds,
                selected.leavesRegion()
                        ? "DRAIN_MOVEMENT_LEAVES_SCC"
                        : "DRAIN_MOVEMENT_FREES_RESOURCE",
                selected.vehicle().getOccupiedRoadId(),
                selected.vehicle().getRoadProgress(),
                selected.targetOccupantIds());
        selected.vehicle().getNavigationState().markRecovery();
        return new Resolution(
                DeadlockStatus.RECOVERING,
                new RecoveryDirective(gate, Optional.of(reservation)),
                reservation.reason());
    }

    private static Set<String> occupantIds(
            team.traffic.domain.vehicle.dynamics.Lane lane) {
        if (lane == null) return Set.of();
        return lane.getVehicles().stream()
                .map(TrafficParticipant::getId)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public record Resolution(
            DeadlockStatus status,
            RecoveryDirective directive,
            String reason) {}

    private record Candidate(
            Vehicle vehicle,
            JunctionMovement movement,
            int targetLaneIndex,
            boolean leavesRegion,
            double waitedSeconds,
            double disruptionCost,
            Set<String> targetOccupantIds) {}

    private static final class MapView {
        private final java.util.Map<String, Vehicle> byId;

        private MapView(List<Vehicle> vehicles) {
            byId = vehicles == null
                    ? java.util.Map.of()
                    : vehicles.stream().collect(Collectors.toMap(
                            Vehicle::getId,
                            Function.identity(),
                            (left, right) -> left,
                            java.util.LinkedHashMap::new));
        }

        private Vehicle vehicle(String id) {
            return byId.get(id);
        }
    }
}
