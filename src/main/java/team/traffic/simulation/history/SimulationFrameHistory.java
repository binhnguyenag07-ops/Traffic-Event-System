package team.traffic.simulation.history;

import java.util.List;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.simulation.SimulationStatus;

/** Bounded in-memory history of immutable renderer frames for one simulation run. */
public final class SimulationFrameHistory {
    public static final int DEFAULT_RETENTION_LIMIT = 900;
    private static final double MILLIS_PER_SECOND = 1000.0;

    private final int retentionLimit;
    private final NavigableMap<Long, SimulationFrame> framesByMillis = new TreeMap<>();
    private String timelineId = UUID.randomUUID().toString();
    private long nextFrameId;

    public SimulationFrameHistory() {
        this(DEFAULT_RETENTION_LIMIT);
    }

    public SimulationFrameHistory(int retentionLimit) {
        if (retentionLimit < 2) throw new IllegalArgumentException("Frame retention limit must be at least 2.");
        this.retentionLimit = retentionLimit;
    }

    public synchronized void reset(
            long tick,
            double elapsedSeconds,
            SimulationStatus status,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals) {
        framesByMillis.clear();
        timelineId = UUID.randomUUID().toString();
        nextFrameId = 0;
        record(tick, elapsedSeconds, status, network, vehicles, events, signals);
    }

    public synchronized SimulationFrame record(
            long tick,
            double elapsedSeconds,
            SimulationStatus status,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals) {
        long millis = toMillis(elapsedSeconds);
        SimulationFrame frame = SimulationFrame.capture(
                timelineId, nextFrameId++, tick, millis / MILLIS_PER_SECOND,
                status, network, vehicles, events, signals);
        framesByMillis.put(millis, frame);
        while (framesByMillis.size() > retentionLimit) framesByMillis.pollFirstEntry();
        return frame;
    }

    public synchronized Optional<SimulationFrame> frameAtOrBefore(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must be finite and not negative.");
        }
        var entry = framesByMillis.floorEntry(toMillis(elapsedSeconds));
        return entry == null ? Optional.empty() : Optional.of(entry.getValue());
    }

    public synchronized Optional<SimulationFrame> currentFrame() {
        return framesByMillis.isEmpty()
                ? Optional.empty()
                : Optional.of(framesByMillis.lastEntry().getValue());
    }

    public synchronized SimulationTimeline timeline() {
        if (framesByMillis.isEmpty()) {
            return new SimulationTimeline(timelineId, 0, 0, 0, retentionLimit, List.of());
        }
        List<Double> seconds = framesByMillis.keySet().stream()
                .map(millis -> millis / MILLIS_PER_SECOND)
                .toList();
        return new SimulationTimeline(
                timelineId,
                seconds.get(0),
                seconds.get(seconds.size() - 1),
                seconds.size(),
                retentionLimit,
                seconds);
    }

    private static long toMillis(double elapsedSeconds) {
        if (!Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Elapsed seconds must be finite and not negative.");
        }
        return Math.round(elapsedSeconds * MILLIS_PER_SECOND);
    }
}
