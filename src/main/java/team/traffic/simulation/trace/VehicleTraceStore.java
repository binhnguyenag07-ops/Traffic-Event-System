package team.traffic.simulation.trace;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;

/** Stores immutable vehicle states for the current simulation run. */
public class VehicleTraceStore {
    public static final int DEFAULT_RETENTION_LIMIT = 5_000;
    private final NavigableMap<Long, Map<String, VehicleTracePoint>> statesByTick = new TreeMap<>();
    private final int retentionLimit;

    public VehicleTraceStore() {
        this(DEFAULT_RETENTION_LIMIT);
    }

    public VehicleTraceStore(int retentionLimit) {
        if (retentionLimit < 2) {
            throw new IllegalArgumentException("Trace retention limit must be at least 2.");
        }
        this.retentionLimit = retentionLimit;
    }

    public void reset(long tick, List<Vehicle> vehicles) {
        statesByTick.clear();
        record(tick, vehicles);
    }

    public void record(long tick, List<Vehicle> vehicles) {
        if (tick < 0) {
            throw new IllegalArgumentException("Trace tick must not be negative.");
        }

        Map<String, VehicleTracePoint> states = new LinkedHashMap<>();
        if (vehicles != null) {
            for (Vehicle vehicle : vehicles) {
                if (vehicle.getStatus() == VehicleStatus.DWELLING) continue;
                VehicleTracePoint point = VehicleTracePoint.from(tick, vehicle);
                states.put(point.vehicleId(), point);
            }
        }
        statesByTick.put(tick, Collections.unmodifiableMap(states));
        while (statesByTick.size() > retentionLimit) statesByTick.pollFirstEntry();
    }

    public VehicleTraceSummary getSummary() {
        if (statesByTick.isEmpty()) {
            return new VehicleTraceSummary(null, null, 0, List.of());
        }

        LinkedHashSet<String> vehicleIds = new LinkedHashSet<>();
        statesByTick.values().forEach(states -> vehicleIds.addAll(states.keySet()));
        return new VehicleTraceSummary(
                statesByTick.firstKey(),
                statesByTick.lastKey(),
                statesByTick.size(),
                List.copyOf(vehicleIds));
    }

    public boolean hasTick(long tick) {
        return statesByTick.containsKey(tick);
    }

    public List<VehicleTracePoint> getStatesAtTick(long tick) {
        Map<String, VehicleTracePoint> states = statesByTick.get(tick);
        return states == null ? List.of() : List.copyOf(states.values());
    }

    public Optional<VehicleTracePoint> getVehicleStateAtTick(String vehicleId, long tick) {
        validateVehicleId(vehicleId);
        Map<String, VehicleTracePoint> states = statesByTick.get(tick);
        return states == null ? Optional.empty() : Optional.ofNullable(states.get(vehicleId));
    }

    public List<VehicleTracePoint> getVehicleTrace(String vehicleId, long fromTick, long toTick) {
        validateVehicleId(vehicleId);
        if (fromTick < 0 || toTick < 0) {
            throw new IllegalArgumentException("Trace range ticks must not be negative.");
        }
        if (fromTick > toTick) {
            throw new IllegalArgumentException("Trace fromTick must not exceed toTick.");
        }

        List<VehicleTracePoint> result = new ArrayList<>();
        for (Map<String, VehicleTracePoint> states
                : statesByTick.subMap(fromTick, true, toTick, true).values()) {
            VehicleTracePoint point = states.get(vehicleId);
            if (point != null) {
                result.add(point);
            }
        }
        return List.copyOf(result);
    }

    public List<VehicleTracePoint> getAllStates() {
        return statesByTick.values().stream()
                .flatMap(states -> states.values().stream())
                .toList();
    }

    private void validateVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.isBlank()) {
            throw new IllegalArgumentException("Trace vehicle id must not be blank.");
        }
    }
}
