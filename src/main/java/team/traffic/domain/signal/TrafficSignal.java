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
}
