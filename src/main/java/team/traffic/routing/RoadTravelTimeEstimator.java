package team.traffic.routing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.dynamics.RoadOccupancy;
import team.traffic.domain.vehicle.dynamics.TrafficParticipant;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/** Deterministic EMA of post-sub-step road travel times and confidence. */
public final class RoadTravelTimeEstimator {
    private final double halfLifeSeconds;
    private final Map<String, Estimate> estimates = new LinkedHashMap<>();
    private double lastUpdateSeconds = Double.NaN;

    public RoadTravelTimeEstimator(double halfLifeSeconds) {
        if (!Double.isFinite(halfLifeSeconds) || halfLifeSeconds <= 0) {
            throw new IllegalArgumentException("EMA half-life must be finite and positive.");
        }
        this.halfLifeSeconds = halfLifeSeconds;
    }

    public synchronized void update(TrafficState state) {
        if (state == null) throw new IllegalArgumentException("Traffic state is required.");
        double now = state.getElapsedSeconds();
        double delta = Double.isFinite(lastUpdateSeconds)
                ? Math.max(0.0, now - lastUpdateSeconds)
                : halfLifeSeconds;
        double alpha = 1.0 - Math.exp(-Math.log(2.0) * delta / halfLifeSeconds);
        for (Road road : state.getNetwork().getRoads().values()) {
            RoadOccupancy occupancy = state.occupancyOf(road.getId());
            double meanSpeed = occupancy == null
                    ? 0.0
                    : occupancy.getVehicles().stream()
                            .mapToDouble(TrafficParticipant::getInstantaneousSpeedMps)
                            .filter(speed -> speed > 0.1)
                            .average()
                            .orElse(0.0);
            double observed = meanSpeed <= 0.1
                    ? road.getFreeFlowTravelTimeSeconds()
                            * (1.0 + 3.0 * road.getCondition().getVehicleDensity())
                    : road.getDistance() * 1_000.0 / meanSpeed;
            observed = Math.max(
                    road.getFreeFlowTravelTimeSeconds(),
                    Math.min(observed, road.getFreeFlowTravelTimeSeconds() * 20.0));
            Estimate previous = estimates.get(road.getId());
            double ema = previous == null
                    ? observed
                    : previous.travelTimeSeconds() + alpha
                            * (observed - previous.travelTimeSeconds());
            double confidence = occupancy == null || occupancy.getVehicleCount() == 0
                    ? 0.35
                    : Math.min(1.0, 0.5 + occupancy.getVehicleCount() * 0.05);
            estimates.put(road.getId(), new Estimate(ema, confidence));
        }
        lastUpdateSeconds = now;
    }

    public synchronized RoutingSnapshot snapshot(
            RoadNetwork network,
            List<TrafficEvent> events,
            double capturedAtSeconds) {
        RoutingSnapshot baseline = RoutingSnapshot.freeFlow(network, events, capturedAtSeconds);
        Map<String, RoutingSnapshot.RoadState> states = new LinkedHashMap<>();
        for (Road road : network.getRoads().values()) {
            RoutingSnapshot.RoadState base = baseline.road(road.getId());
            Estimate estimate = estimates.get(road.getId());
            states.put(road.getId(), new RoutingSnapshot.RoadState(
                    estimate == null ? base.travelTimeSeconds() : estimate.travelTimeSeconds(),
                    estimate == null ? base.confidence() : estimate.confidence(),
                    base.networkRoutable(),
                    base.temporarilyFull(),
                    base.dynamicallyAffected(),
                    0));
        }
        return new RoutingSnapshot(capturedAtSeconds, states);
    }

    private record Estimate(double travelTimeSeconds, double confidence) {}
}
