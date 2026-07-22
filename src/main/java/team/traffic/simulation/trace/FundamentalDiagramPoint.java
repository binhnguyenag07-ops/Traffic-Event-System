package team.traffic.simulation.trace;

/** One density bin in a traffic fundamental diagram. */
public record FundamentalDiagramPoint(
        double densityVehPerKmPerLane,
        double meanSpeedMps,
        double flowVehPerHourPerLane,
        int sampleCount) {
}
