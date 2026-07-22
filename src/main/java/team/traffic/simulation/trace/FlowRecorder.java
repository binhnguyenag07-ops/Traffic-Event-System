package team.traffic.simulation.trace;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.RoadLayout;

/** Captures density, speed and flow without mutating the simulation. */
public final class FlowRecorder {
    private final RoadLayout roadLayout;
    private final List<RoadFlowSample> samples = new ArrayList<>();

    public FlowRecorder() {
        this(new RoadDataRoadLayout());
    }

    public FlowRecorder(RoadLayout roadLayout) {
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        this.roadLayout = roadLayout;
    }

    public void reset() {
        samples.clear();
    }

    public void record(long tick, double elapsedSeconds, RoadNetwork network, List<Vehicle> vehicles) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        Map<String, List<Vehicle>> byRoad = new LinkedHashMap<>();
        if (vehicles != null) {
            for (Vehicle vehicle : vehicles) {
                if (vehicle.getOccupiedRoadId() != null) {
                    byRoad.computeIfAbsent(vehicle.getOccupiedRoadId(), ignored -> new ArrayList<>()).add(vehicle);
                }
            }
        }
        byRoad.forEach((roadId, onRoad) -> {
            Road road = network.getRoad(roadId);
            if (road == null || road.getDistance() <= 0) return;
            double meanSpeed = onRoad.stream().mapToDouble(Vehicle::getInstantaneousSpeedMps).average().orElse(0.0);
            double laneKilometres = road.getDistance() * roadLayout.laneCountOf(road);
            double density = onRoad.size() / laneKilometres;
            double flow = density * meanSpeed * 3.6;
            samples.add(new RoadFlowSample(roadId, tick, elapsedSeconds, onRoad.size(), meanSpeed, density, flow));
        });
    }

    public List<RoadFlowSample> getSamples() {
        return List.copyOf(samples);
    }

    public List<RoadFlowSample> getSamplesForRoad(String roadId) {
        if (roadId == null || roadId.isBlank()) throw new IllegalArgumentException("Road id is required.");
        return samples.stream().filter(sample -> roadId.equals(sample.roadId())).toList();
    }
}
