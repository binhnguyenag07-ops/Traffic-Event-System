package team.traffic.domain.vehicle.dynamics;

/** Nearest visible physical event obstruction in a lane. */
public record ObstructionObservation(
        String eventId,
        double distanceAheadMetres,
        int laneIndex,
        boolean fullySealsLane) {

    public ObstructionObservation {
        if (eventId == null || eventId.isBlank()
                || !Double.isFinite(distanceAheadMetres) || distanceAheadMetres < 0
                || laneIndex < 0) {
            throw new IllegalArgumentException("Obstruction observation values are invalid.");
        }
    }
}
