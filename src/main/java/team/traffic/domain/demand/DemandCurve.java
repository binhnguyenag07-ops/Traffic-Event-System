package team.traffic.domain.demand;

import java.util.List;

/** Normalized distribution of a daily flow across the 24 clock hours. */
public record DemandCurve(String id, List<Double> hourlyWeights) {
    private static final double SUM_TOLERANCE = 1e-6;

    public DemandCurve {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Demand curve id is required");
        if (hourlyWeights == null || hourlyWeights.size() != 24) {
            throw new IllegalArgumentException("Demand curve must contain exactly 24 hourly weights");
        }
        hourlyWeights = List.copyOf(hourlyWeights);
        double sum = 0;
        for (Double weight : hourlyWeights) {
            if (weight == null || !Double.isFinite(weight) || weight < 0) {
                throw new IllegalArgumentException("Demand curve weights must be finite and non-negative");
            }
            sum += weight;
        }
        if (Math.abs(sum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("Demand curve weights must sum to 1, got " + sum);
        }
    }

    public double weightAtHour(int hour) {
        if (hour < 0 || hour >= 24) throw new IllegalArgumentException("Hour must be between 0 and 23");
        return hourlyWeights.get(hour);
    }
}
