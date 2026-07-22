package team.traffic.simulation.trace;

/** Vehicle trajectory point measured from the start of one road. */
public record TimeSpacePoint(
        String vehicleId,
        long tick,
        double positionMetres,
        double speedKph) {
}
