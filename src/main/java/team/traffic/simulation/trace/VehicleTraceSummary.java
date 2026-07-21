package team.traffic.simulation.trace;

import java.util.List;

public record VehicleTraceSummary(
        Long firstTick,
        Long lastTick,
        int recordedTickCount,
        List<String> vehicleIds) {

    public VehicleTraceSummary {
        if (recordedTickCount < 0) {
            throw new IllegalArgumentException("Recorded tick count must not be negative.");
        }
        vehicleIds = vehicleIds == null ? List.of() : List.copyOf(vehicleIds);
    }
}
