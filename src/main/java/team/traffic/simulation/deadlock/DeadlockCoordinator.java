package team.traffic.simulation.deadlock;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.Lane;
import team.traffic.domain.vehicle.dynamics.RoadOccupancy;
import team.traffic.domain.vehicle.dynamics.TrafficParticipant;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.simulation.AdaptiveTrafficConfig;

/** Owns detector state and next-sub-step recovery directives. */
public final class DeadlockCoordinator {
    private final boolean enabled;
    private final DeadlockMetrics metrics = new DeadlockMetrics();
    private final DeadlockDetector detector;
    private final DeadlockResolver resolver;
    private RecoveryDirective directive = RecoveryDirective.NONE;

    public DeadlockCoordinator(
            boolean enabled,
            AdaptiveTrafficConfig.Deadlock config) {
        if (config == null) throw new IllegalArgumentException("Deadlock config is required.");
        this.enabled = enabled;
        this.detector = new DeadlockDetector(
                config.scanIntervalSeconds(),
                config.confirmSeconds(),
                metrics);
        this.resolver = new DeadlockResolver(config.reservationSeconds());
    }

    public void applyTo(TrafficState state) {
        if (enabled && state != null) state.applyRecoveryControl(directive);
    }

    public void observe(
            double simulationSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            TrafficState state) {
        if (!enabled) return;
        evaluateExistingReservation(simulationSeconds, network, vehicles, state);
        if (directive.gate() != null
                && directive.reservation().isEmpty()
                && directive.gate().isExpired(simulationSeconds)
                && detector.contains(directive.gate().regionFingerprint())) {
            // Strictly unresolvable is a statement about the current physical
            // snapshot, not a permanent label. Keep the gate closed, renew the
            // diagnosis, and re-evaluate after each bounded reservation window.
            detector.markConfirmed(directive.gate().regionFingerprint());
        }
        List<DeadlockCycle> cycles =
                detector.scan(simulationSeconds, vehicles, state);
        if (directive.gate() != null
                && cycles.stream().noneMatch(cycle ->
                        cycle.fingerprint().equals(directive.gate().regionFingerprint()))) {
            detector.markRecovered(directive.gate().regionFingerprint());
            directive = RecoveryDirective.NONE;
        }
        if (directive.reservation().isPresent()) return;

        cycles.stream()
                .filter(cycle -> cycle.status() == DeadlockStatus.CONFIRMED)
                .findFirst()
                .ifPresent(cycle -> {
                    DeadlockResolver.Resolution resolution = resolver.resolve(
                            cycle, simulationSeconds, network, vehicles, state);
                    directive = resolution.directive();
                    if (resolution.status() == DeadlockStatus.RECOVERING) {
                        detector.markRecovering(cycle.fingerprint());
                        metrics.recordReservationIssued();
                    } else {
                        detector.markUnresolvable(
                                cycle.fingerprint(),
                                resolution.reason());
                    }
                });
    }

    public DeadlockMetrics metrics() {
        return metrics;
    }

    public List<DeadlockCycle> cycles() {
        return detector.activeCycles();
    }

    public RecoveryDirective directive() {
        return directive;
    }

    private void evaluateExistingReservation(
            double simulationSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            TrafficState state) {
        Optional<RecoveryReservation> selected = directive.reservation();
        if (selected.isEmpty()) return;
        RecoveryReservation reservation = selected.get();
        Vehicle vehicle = vehicles.stream()
                .filter(candidate -> reservation.vehicleId().equals(candidate.getId()))
                .findFirst()
                .orElse(null);
        if (vehicle != null && completedReservedMovement(reservation, vehicle)) {
            metrics.recordReservationSucceeded();
            detector.markRecovered(reservation.regionFingerprint());
            directive = RecoveryDirective.NONE;
            return;
        }
        if (reservation.isExpired(simulationSeconds)
                || !targetSnapshotStillValid(reservation, vehicle, state)) {
            metrics.recordReservationExpired();
            detector.markConfirmed(reservation.regionFingerprint());
            directive = new RecoveryDirective(directive.gate(), Optional.empty());
        }
    }

    private boolean completedReservedMovement(
            RecoveryReservation reservation,
            Vehicle vehicle) {
        // A junction drain frees its resource only after the selected vehicle
        // has actually left the source road. Creeping towards the stop line is
        // ordinary progress, but is not a completed recovery grant.
        return !java.util.Objects.equals(
                reservation.baselineRoadId(),
                vehicle.getOccupiedRoadId());
    }

    private boolean targetSnapshotStillValid(
            RecoveryReservation reservation,
            Vehicle vehicle,
            TrafficState state) {
        if (vehicle == null) return false;
        RoadOccupancy occupancy =
                state.occupancyOf(reservation.movement().toRoadId());
        Lane lane = occupancy == null ? null : occupancy.lane(reservation.targetLaneIndex());
        if (lane == null || !lane.canAdmit(vehicle)) return false;
        Set<String> occupants = lane.getVehicles().stream()
                .map(TrafficParticipant::getId)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        return occupants.equals(reservation.targetOccupantIds());
    }
}
