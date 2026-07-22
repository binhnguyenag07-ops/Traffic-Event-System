package team.traffic.simulation.history;

import java.util.List;

/** Retained frame index for the current simulation run. */
public record SimulationTimeline(
        String timelineId,
        double firstElapsedSeconds,
        double lastElapsedSeconds,
        int retainedFrameCount,
        int retentionLimit,
        List<Double> recordedSeconds) {

    public SimulationTimeline {
        recordedSeconds = recordedSeconds == null ? List.of() : List.copyOf(recordedSeconds);
    }
}
