package team.traffic.simulation.trace;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.network.Road;

/** All recorded trajectories along one road, ready for a time-space chart. */
public record TimeSpaceDiagram(String roadId, List<TimeSpacePoint> points) {
    public TimeSpaceDiagram {
        if (roadId == null || roadId.isBlank()) throw new IllegalArgumentException("Road id is required.");
        points = points == null ? List.of() : List.copyOf(points);
    }

    public static TimeSpaceDiagram from(Road road, VehicleTraceStore traceStore) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        if (traceStore == null) throw new IllegalArgumentException("Trace store is required.");
        List<TimeSpacePoint> points = new ArrayList<>();
        for (VehicleTracePoint trace : traceStore.getAllStates()) {
            if (road.getId().equals(trace.currentRoadId())) {
                points.add(new TimeSpacePoint(
                        trace.vehicleId(), trace.tick(),
                        trace.roadProgress() * road.getDistance() * 1_000.0,
                        trace.instantaneousSpeed()));
            }
        }
        return new TimeSpaceDiagram(road.getId(), points);
    }
}
