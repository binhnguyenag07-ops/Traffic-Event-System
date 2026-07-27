package team.traffic.domain.vehicle.dynamics;

import java.util.Optional;

/** Local facts about the current or one immediately adjacent lane. */
public record LaneObservation(
        int laneIndex,
        double centreOffsetMetres,
        double forwardGapMetres,
        double rearGapMetres,
        Optional<ObstructionObservation> obstruction,
        double observedMeanSpeedMps,
        boolean presentToNextManoeuvre) {

    public LaneObservation {
        if (laneIndex < 0 || !Double.isFinite(centreOffsetMetres)
                || Double.isNaN(forwardGapMetres) || forwardGapMetres < 0
                || Double.isNaN(rearGapMetres) || rearGapMetres < 0
                || !Double.isFinite(observedMeanSpeedMps) || observedMeanSpeedMps < 0) {
            throw new IllegalArgumentException("Lane observation values are invalid.");
        }
        obstruction = obstruction == null ? Optional.empty() : obstruction;
    }
}
