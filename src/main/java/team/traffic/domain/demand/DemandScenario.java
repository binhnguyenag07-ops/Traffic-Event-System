package team.traffic.domain.demand;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import team.traffic.simulation.SimulationClock;

public record DemandScenario(
        double startTimeOfDaySeconds,
        double durationSeconds,
        long seed,
        double scale,
        List<DemandSource> sources,
        Map<String, ZoneDefinition> zones,
        DemandParameters parameters) {

    public DemandScenario {
        if (!Double.isFinite(startTimeOfDaySeconds)) {
            throw new IllegalArgumentException("Scenario start time must be finite");
        }
        startTimeOfDaySeconds = SimulationClock.timeOfDayAt(startTimeOfDaySeconds, 0);
        if (!Double.isFinite(durationSeconds) || durationSeconds <= 0) {
            throw new IllegalArgumentException("Scenario duration must be positive");
        }
        if (durationSeconds > SimulationClock.SECONDS_PER_DAY) {
            throw new IllegalArgumentException(
                    "Demand scenarios are limited to one simulated day");
        }
        if (!Double.isFinite(scale) || scale < 0) {
            throw new IllegalArgumentException("Scenario scale must be finite and non-negative");
        }
        if (sources == null) throw new IllegalArgumentException("Demand sources are required");
        sources = List.copyOf(sources);
        zones = zones == null ? Map.of() : Map.copyOf(zones);
        if (parameters == null) throw new IllegalArgumentException("Demand parameters are required");
        Set<String> sourceIds = new HashSet<>();
        Set<String> flowIds = new HashSet<>();
        for (DemandSource source : sources) {
            if (!sourceIds.add(source.id())) {
                throw new IllegalArgumentException("Duplicate demand source id: " + source.id());
            }
            for (FlowSpec flow : source.flows()) {
                if (!flowIds.add(flow.id())) {
                    throw new IllegalArgumentException("Flow ids must be unique across the scenario: " + flow.id());
                }
            }
        }
    }
}
