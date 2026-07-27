package team.traffic.domain.signal;

import java.util.List;

/** Repeating signal controller attached to a junction rather than a road. */
public final class TrafficSignal {
    private final String junctionId;
    private final List<SignalPhase> phases;
    private final double offsetSeconds;
    private final double cycleDurationSeconds;

    public TrafficSignal(String junctionId, List<SignalPhase> phases) {
        this(junctionId, phases, 0.0);
    }

    public TrafficSignal(String junctionId, List<SignalPhase> phases, double offsetSeconds) {
        if (junctionId == null || junctionId.isBlank()) throw new IllegalArgumentException("Junction id is required.");
        if (phases == null || phases.isEmpty() || phases.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("At least one signal phase is required.");
        }
        if (!Double.isFinite(offsetSeconds) || offsetSeconds < 0) {
            throw new IllegalArgumentException("Signal offset must not be negative.");
        }
        this.junctionId = junctionId;
        this.phases = List.copyOf(phases);
        this.offsetSeconds = offsetSeconds;
        this.cycleDurationSeconds = phases.stream().mapToDouble(SignalPhase::durationSeconds).sum();
    }

    public String getJunctionId() { return junctionId; }
    public List<SignalPhase> getPhases() { return phases; }
    public double getOffsetSeconds() { return offsetSeconds; }
    public double getCycleDurationSeconds() { return cycleDurationSeconds; }

    public SignalPhase phaseAt(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must not be negative.");
        }
        double withinCycle = (elapsedSeconds + offsetSeconds) % cycleDurationSeconds;
        for (SignalPhase phase : phases) {
            if (withinCycle < phase.durationSeconds()) return phase;
            withinCycle -= phase.durationSeconds();
        }
        return phases.get(phases.size() - 1);
    }

    public boolean isGreenFor(String incomingRoadId, double elapsedSeconds) {
        return incomingRoadId == null || phaseAt(elapsedSeconds).greenIncomingRoadIds().contains(incomingRoadId);
    }

    /** Simulated seconds until the active phase changes. */
    public double secondsUntilPhaseChange(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must not be negative.");
        }
        double withinCycle = (elapsedSeconds + offsetSeconds) % cycleDurationSeconds;
        for (SignalPhase phase : phases) {
            if (withinCycle < phase.durationSeconds()) {
                return phase.durationSeconds() - withinCycle;
            }
            withinCycle -= phase.durationSeconds();
        }
        return phases.get(phases.size() - 1).durationSeconds();
    }

    /** Simulated seconds until this incoming approach next receives green. */
    public double secondsUntilGreenFor(
            String incomingRoadId,
            double elapsedSeconds) {
        if (incomingRoadId == null) return 0.0;
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must not be negative.");
        }
        double withinCycle = (elapsedSeconds + offsetSeconds) % cycleDurationSeconds;
        int activeIndex = phases.size() - 1;
        double remainingInActive = phases.get(activeIndex).durationSeconds();
        for (int index = 0; index < phases.size(); index++) {
            SignalPhase phase = phases.get(index);
            if (withinCycle < phase.durationSeconds()) {
                activeIndex = index;
                remainingInActive = phase.durationSeconds() - withinCycle;
                break;
            }
            withinCycle -= phase.durationSeconds();
        }
        if (phases.get(activeIndex).greenIncomingRoadIds().contains(incomingRoadId)) {
            return 0.0;
        }
        double wait = remainingInActive;
        for (int offset = 1; offset <= phases.size(); offset++) {
            SignalPhase phase = phases.get((activeIndex + offset) % phases.size());
            if (phase.greenIncomingRoadIds().contains(incomingRoadId)) {
                return wait;
            }
            wait += phase.durationSeconds();
        }
        return Double.POSITIVE_INFINITY;
    }
}
