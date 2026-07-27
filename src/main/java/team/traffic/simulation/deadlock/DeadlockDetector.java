package team.traffic.simulation.deadlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/**
 * Low-cadence persistent SCC detector. A changing queue produces a changing
 * fingerprint and restarts confirmation rather than becoming a false lock.
 */
public final class DeadlockDetector {
    private static final String MOVEMENT_PREFIX = "JUNCTION_MOVEMENT:";

    private final double scanIntervalSeconds;
    private final double confirmSeconds;
    private final DeadlockMetrics metrics;
    private final Map<String, Evidence> evidenceByFingerprint = new LinkedHashMap<>();
    private double nextScanAtSeconds;

    public DeadlockDetector(
            double scanIntervalSeconds,
            double confirmSeconds,
            DeadlockMetrics metrics) {
        if (!Double.isFinite(scanIntervalSeconds) || scanIntervalSeconds <= 0
                || !Double.isFinite(confirmSeconds) || confirmSeconds <= 0
                || metrics == null) {
            throw new IllegalArgumentException("Deadlock detector configuration is invalid.");
        }
        this.scanIntervalSeconds = scanIntervalSeconds;
        this.confirmSeconds = confirmSeconds;
        this.metrics = metrics;
    }

    public List<DeadlockCycle> scan(
            double simulationSeconds,
            List<Vehicle> vehicles,
            TrafficState state) {
        if (simulationSeconds + 1e-9 < nextScanAtSeconds) {
            return activeCycles();
        }
        nextScanAtSeconds = simulationSeconds + scanIntervalSeconds;
        metrics.scan();

        List<WaitForGraph.Component> components =
                WaitForGraph.capture(vehicles, state).cyclicComponents();
        Set<String> seen = new TreeSet<>();
        for (WaitForGraph.Component component : components) {
            seen.add(component.fingerprint());
            Evidence evidence = evidenceByFingerprint.get(component.fingerprint());
            if (evidence == null) {
                evidence = new Evidence(
                        component,
                        simulationSeconds,
                        requiredConfirmationSeconds(component, state));
                evidenceByFingerprint.put(component.fingerprint(), evidence);
                metrics.suspect();
            }
            evidence.lastObservedAtSeconds = simulationSeconds;
            if (evidence.status == DeadlockStatus.SUSPECTED
                    && simulationSeconds - evidence.firstObservedAtSeconds
                            + 1e-9 >= evidence.requiredConfirmationSeconds) {
                evidence.status = DeadlockStatus.CONFIRMED;
                metrics.confirm();
            }
        }
        evidenceByFingerprint.entrySet().removeIf(entry -> !seen.contains(entry.getKey()));
        return activeCycles();
    }

    public List<DeadlockCycle> activeCycles() {
        return evidenceByFingerprint.values().stream()
                .map(Evidence::snapshot)
                .sorted(java.util.Comparator.comparing(DeadlockCycle::fingerprint))
                .toList();
    }

    public void markRecovering(String fingerprint) {
        Evidence evidence = evidenceByFingerprint.get(fingerprint);
        if (evidence != null && evidence.status == DeadlockStatus.CONFIRMED) {
            evidence.status = DeadlockStatus.RECOVERING;
            evidence.diagnosis = "STRICT_DRAIN_RESERVED";
        }
    }

    public void markUnresolvable(String fingerprint) {
        markUnresolvable(fingerprint, "NO_FREE_STORAGE_OR_LEGAL_EXIT");
    }

    public void markUnresolvable(String fingerprint, String diagnosis) {
        Evidence evidence = evidenceByFingerprint.get(fingerprint);
        if (evidence != null && evidence.status != DeadlockStatus.UNRESOLVABLE_STRICT) {
            evidence.status = DeadlockStatus.UNRESOLVABLE_STRICT;
            evidence.diagnosis = diagnosis;
            metrics.recordUnresolvable();
        }
    }

    public void markConfirmed(String fingerprint) {
        Evidence evidence = evidenceByFingerprint.get(fingerprint);
        if (evidence != null) {
            evidence.status = DeadlockStatus.CONFIRMED;
            evidence.diagnosis = null;
        }
    }

    public boolean contains(String fingerprint) {
        return evidenceByFingerprint.containsKey(fingerprint);
    }

    public void markRecovered(String fingerprint) {
        if (evidenceByFingerprint.remove(fingerprint) != null) {
            metrics.recover();
        }
    }

    private double requiredConfirmationSeconds(
            WaitForGraph.Component component,
            TrafficState state) {
        double required = confirmSeconds;
        for (String resource : component.resourceKeys()) {
            if (!resource.startsWith(MOVEMENT_PREFIX)) continue;
            String remainder = resource.substring(MOVEMENT_PREFIX.length());
            int separator = remainder.indexOf(':');
            String junctionId = separator < 0 ? remainder : remainder.substring(0, separator);
            required = Math.max(
                    required,
                    state.signalAt(junctionId)
                            .map(TrafficSignal::getCycleDurationSeconds)
                            .orElse(0.0));
        }
        return required;
    }

    private final class Evidence {
        private final WaitForGraph.Component component;
        private final double firstObservedAtSeconds;
        private final double requiredConfirmationSeconds;
        private double lastObservedAtSeconds;
        private DeadlockStatus status = DeadlockStatus.SUSPECTED;
        private String diagnosis;

        private Evidence(
                WaitForGraph.Component component,
                double firstObservedAtSeconds,
                double requiredConfirmationSeconds) {
            this.component = component;
            this.firstObservedAtSeconds = firstObservedAtSeconds;
            this.lastObservedAtSeconds = firstObservedAtSeconds;
            this.requiredConfirmationSeconds = requiredConfirmationSeconds;
        }

        private DeadlockCycle snapshot() {
            return new DeadlockCycle(
                    component.fingerprint(),
                    component.vehicleIds(),
                    component.resourceKeys(),
                    firstObservedAtSeconds,
                    lastObservedAtSeconds,
                    status,
                    diagnosis);
        }
    }
}
