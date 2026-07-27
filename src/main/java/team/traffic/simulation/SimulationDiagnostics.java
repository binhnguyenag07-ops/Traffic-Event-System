package team.traffic.simulation;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.simulation.deadlock.DeadlockCycle;
import team.traffic.simulation.deadlock.DeadlockMetrics;
import team.traffic.simulation.navigation.AdaptiveNavigationMetrics;
import team.traffic.simulation.navigation.TrafficInformationMetrics;

/** Immutable API/debug snapshot of adaptive traffic decisions. */
public record SimulationDiagnostics(
        AdaptiveNavigationMetrics navigation,
        TrafficInformationMetrics information,
        DeadlockMetricsView deadlock,
        List<DeadlockCycle> activeDeadlocks,
        Map<WaitCause, Double> waitingSecondsByCause) {
    public SimulationDiagnostics {
        activeDeadlocks = activeDeadlocks == null
                ? List.of()
                : List.copyOf(activeDeadlocks);
        waitingSecondsByCause = waitingSecondsByCause == null
                ? Map.of()
                : Map.copyOf(waitingSecondsByCause);
    }

    public static SimulationDiagnostics from(
            AdaptiveNavigationMetrics navigation,
            TrafficInformationMetrics information,
            DeadlockMetrics deadlock,
            List<DeadlockCycle> activeDeadlocks) {
        return from(
                navigation,
                information,
                deadlock,
                activeDeadlocks,
                List.of());
    }

    public static SimulationDiagnostics from(
            AdaptiveNavigationMetrics navigation,
            TrafficInformationMetrics information,
            DeadlockMetrics deadlock,
            List<DeadlockCycle> activeDeadlocks,
            List<Vehicle> vehicles) {
        return from(
                navigation,
                information,
                deadlock,
                activeDeadlocks,
                vehicles,
                Map.of());
    }

    public static SimulationDiagnostics from(
            AdaptiveNavigationMetrics navigation,
            TrafficInformationMetrics information,
            DeadlockMetrics deadlock,
            List<DeadlockCycle> activeDeadlocks,
            List<Vehicle> vehicles,
            Map<WaitCause, Double> priorWaitingSecondsByCause) {
        return new SimulationDiagnostics(
                navigation,
                information,
                new DeadlockMetricsView(
                        deadlock.scans(),
                        deadlock.suspected(),
                        deadlock.confirmed(),
                        deadlock.recovered(),
                        deadlock.unresolvable(),
                        deadlock.reservationIssued(),
                        deadlock.reservationSucceeded(),
                        deadlock.reservationExpired(),
                        deadlock.assistedRecoveries()),
                activeDeadlocks,
                aggregateWaiting(vehicles, priorWaitingSecondsByCause));
    }

    private static Map<WaitCause, Double> aggregateWaiting(
            List<Vehicle> vehicles,
            Map<WaitCause, Double> priorWaitingSecondsByCause) {
        Map<WaitCause, Double> totals = new EnumMap<>(WaitCause.class);
        if (priorWaitingSecondsByCause != null) {
            totals.putAll(priorWaitingSecondsByCause);
        }
        if (vehicles != null) {
            vehicles.forEach(vehicle ->
                    vehicle.getTrip().getWaitingSecondsByCause().forEach(
                            (cause, seconds) ->
                                    totals.merge(cause, seconds, Double::sum)));
        }
        return Map.copyOf(totals);
    }

    public record DeadlockMetricsView(
            long scans,
            long suspected,
            long confirmed,
            long recovered,
            long unresolvable,
            long reservationIssued,
            long reservationSucceeded,
            long reservationExpired,
            long assistedRecoveries) {}
}
