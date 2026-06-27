package team.traffic.simulation;

public record EventConfig(
        String id,
        String type,
        String affectedRoadId,
        long startTick,
        long durationTicks) {}
