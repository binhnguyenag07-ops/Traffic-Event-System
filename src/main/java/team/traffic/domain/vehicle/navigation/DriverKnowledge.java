package team.traffic.domain.vehicle.navigation;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persistent per-driver information; never exposes the authoritative event list. */
public final class DriverKnowledge {
    private final Map<String, RoadAdvisory> advisoriesById = new LinkedHashMap<>();
    private long version;

    public synchronized boolean receive(RoadAdvisory advisory) {
        if (advisory == null) throw new IllegalArgumentException("Advisory is required.");
        RoadAdvisory previous = advisoriesById.get(advisory.advisoryId());
        if (previous != null && previous.receivedAtSeconds() > advisory.receivedAtSeconds()) {
            return false;
        }
        if (advisory.equals(previous)) return false;
        advisoriesById.put(advisory.advisoryId(), advisory);
        version++;
        return true;
    }

    public synchronized List<RoadAdvisory> activeAt(double simulationSeconds) {
        return advisoriesById.values().stream()
                .filter(advisory -> advisory.isValidAt(simulationSeconds))
                .sorted(Comparator.comparing(RoadAdvisory::roadId)
                        .thenComparing(RoadAdvisory::advisoryId))
                .toList();
    }

    public synchronized List<RoadAdvisory> forRoad(
            String roadId,
            double simulationSeconds) {
        return activeAt(simulationSeconds).stream()
                .filter(advisory -> advisory.roadId().equals(roadId))
                .toList();
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized String signature(double simulationSeconds) {
        return activeAt(simulationSeconds).stream()
                .map(advisory -> advisory.advisoryId() + ":" + advisory.kind()
                        + ":" + Math.round(advisory.expectedDelaySeconds()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("EMPTY");
    }
}
