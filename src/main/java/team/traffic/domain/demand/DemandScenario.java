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
        double warmUpSeconds,
        boolean automaticCollisionEvents,
        List<DemandSource> sources,
        Map<String, ZoneDefinition> zones,
        DemandParameters parameters) {

    public DemandScenario(
            double startTimeOfDaySeconds,
            double durationSeconds,
            long seed,
            double scale,
            double warmUpSeconds,
            List<DemandSource> sources,
            Map<String, ZoneDefinition> zones,
            DemandParameters parameters) {
        this(startTimeOfDaySeconds, durationSeconds, seed, scale, warmUpSeconds,
                true, sources, zones, parameters);
    }

    public DemandScenario(
            double startTimeOfDaySeconds,
            double durationSeconds,
            long seed,
            double scale,
            List<DemandSource> sources,
            Map<String, ZoneDefinition> zones,
            DemandParameters parameters) {
        this(startTimeOfDaySeconds, durationSeconds, seed, scale, 0, true,
                sources, zones, parameters);
    }

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
        if (!Double.isFinite(warmUpSeconds)
                || warmUpSeconds < 0
                || warmUpSeconds >= durationSeconds) {
            throw new IllegalArgumentException(
                    "Scenario warm-up must be finite, non-negative, and shorter than its duration");
        }
        if (sources == null) throw new IllegalArgumentException("Demand sources are required");
        sources = List.copyOf(sources);
        zones = zones == null ? Map.of() : Map.copyOf(zones);
        if (parameters == null) throw new IllegalArgumentException("Demand parameters are required");
        Set<String> sourceIds = new HashSet<>();
        for (DemandSource source : sources) {
            if (!sourceIds.add(source.id())) {
                throw new IllegalArgumentException("Duplicate demand source id: " + source.id());
            }
        }
    }
}
