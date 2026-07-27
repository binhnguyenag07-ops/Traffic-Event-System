package team.traffic.simulation.navigation;

/** Immutable delivery counters for diagnostics. */
public record TrafficInformationMetrics(
        long delivered,
        long missed,
        double totalDeliveryLatencySeconds) {

    public double meanDeliveryLatencySeconds() {
        return delivered == 0 ? 0.0 : totalDeliveryLatencySeconds / delivered;
    }

    public double getMeanDeliveryLatencySeconds() {
        return meanDeliveryLatencySeconds();
    }
}
