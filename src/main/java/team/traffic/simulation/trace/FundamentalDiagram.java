package team.traffic.simulation.trace;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Aggregates flow samples into stable density bins for charting. */
public final class FundamentalDiagram {
    public static final double DEFAULT_BIN_WIDTH_VEH_PER_KM = 5.0;

    private final List<FundamentalDiagramPoint> points;

    public FundamentalDiagram(List<RoadFlowSample> samples) {
        this(samples, DEFAULT_BIN_WIDTH_VEH_PER_KM);
    }

    public FundamentalDiagram(List<RoadFlowSample> samples, double binWidth) {
        if (!Double.isFinite(binWidth) || binWidth <= 0) {
            throw new IllegalArgumentException("Density bin width must be positive.");
        }
        Map<Integer, List<RoadFlowSample>> bins = new TreeMap<>();
        if (samples != null) {
            for (RoadFlowSample sample : samples) {
                int bin = (int) Math.floor(sample.densityVehPerKmPerLane() / binWidth);
                bins.computeIfAbsent(bin, ignored -> new ArrayList<>()).add(sample);
            }
        }
        this.points = bins.values().stream().map(FundamentalDiagram::aggregate).toList();
    }

    public List<FundamentalDiagramPoint> points() {
        return points;
    }

    private static FundamentalDiagramPoint aggregate(List<RoadFlowSample> samples) {
        return new FundamentalDiagramPoint(
                samples.stream().mapToDouble(RoadFlowSample::densityVehPerKmPerLane).average().orElse(0),
                samples.stream().mapToDouble(RoadFlowSample::meanSpeedMps).average().orElse(0),
                samples.stream().mapToDouble(RoadFlowSample::flowVehPerHourPerLane).average().orElse(0),
                samples.size());
    }
}
