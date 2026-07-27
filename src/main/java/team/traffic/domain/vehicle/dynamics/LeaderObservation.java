package team.traffic.domain.vehicle.dynamics;

/** Nearest laterally-overlapping vehicle ahead. */
public record LeaderObservation(
        String vehicleId,
        double bumperGapMetres,
        double speedMps,
        double relativeSpeedMps,
        boolean stationary) {

    public LeaderObservation {
        if (vehicleId == null || vehicleId.isBlank()
                || !Double.isFinite(bumperGapMetres) || bumperGapMetres < 0
                || !Double.isFinite(speedMps) || speedMps < 0
                || !Double.isFinite(relativeSpeedMps)) {
            throw new IllegalArgumentException("Leader observation values are invalid.");
        }
    }
}
