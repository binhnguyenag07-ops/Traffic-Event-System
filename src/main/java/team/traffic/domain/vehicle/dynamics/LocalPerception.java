package team.traffic.domain.vehicle.dynamics;

import java.util.List;
import java.util.Optional;

/** Immutable local facts visible to one vehicle at one physical sub-step. */
public record LocalPerception(
        double observedAtSeconds,
        double sensorHorizonMetres,
        VehicleLocation location,
        Optional<LeaderObservation> leader,
        List<LaneObservation> reachableLanes,
        Optional<SignalObservation> signal,
        Optional<JunctionObservation> junction,
        ExitReachability exitReachability) {

    public LocalPerception {
        if (!Double.isFinite(observedAtSeconds) || observedAtSeconds < 0
                || !Double.isFinite(sensorHorizonMetres) || sensorHorizonMetres <= 0
                || location == null || exitReachability == null) {
            throw new IllegalArgumentException("Perception time, horizon, location, and reachability are required.");
        }
        leader = leader == null ? Optional.empty() : leader;
        reachableLanes = reachableLanes == null ? List.of() : List.copyOf(reachableLanes);
        signal = signal == null ? Optional.empty() : signal;
        junction = junction == null ? Optional.empty() : junction;
    }
}
