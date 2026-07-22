package team.traffic.api.dto;

import java.util.List;
import team.traffic.domain.signal.SignalPhase;
import team.traffic.domain.signal.TrafficSignal;

public record TrafficSignalDto(
        String junctionId,
        List<SignalPhase> phases,
        double offsetSeconds,
        double cycleDurationSeconds) {
    public TrafficSignalDto(TrafficSignal signal) {
        this(signal.getJunctionId(), signal.getPhases(),
                signal.getOffsetSeconds(), signal.getCycleDurationSeconds());
    }
}
