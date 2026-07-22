package team.traffic.domain.signal;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.dynamics.Junction;
import team.traffic.domain.vehicle.pose.PolylineGeometry;

/** Builds deterministic two-axis plans from derived junction geometry. */
public final class SignalPlan {
    public static final double DEFAULT_GREEN_SECONDS = 30.0;

    private SignalPlan() {}

    public static List<TrafficSignal> defaultSignals(RoadNetwork network) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        List<TrafficSignal> signals = new ArrayList<>();
        for (String intersectionId : network.getIntersections().keySet()) {
            Junction junction = Junction.at(network, intersectionId);
            if (junction.isRealJunction() && !junction.incomingRoads().isEmpty()) {
                signals.add(defaultFor(junction, network));
            }
        }
        return List.copyOf(signals);
    }

    public static TrafficSignal defaultFor(Junction junction, RoadNetwork network) {
        if (junction == null || network == null) throw new IllegalArgumentException("Junction and network are required.");
        List<Road> incoming = junction.incomingRoads();
        if (incoming.isEmpty()) {
            throw new IllegalArgumentException("A signal requires at least one incoming road.");
        }
        double referenceAxis = approachBearing(incoming.get(0), network) % 180.0;
        Set<String> firstAxis = new LinkedHashSet<>();
        Set<String> secondAxis = new LinkedHashSet<>();
        for (Road road : incoming) {
            double axis = approachBearing(road, network) % 180.0;
            double difference = Math.abs(axis - referenceAxis);
            difference = Math.min(difference, 180.0 - difference);
            (difference <= 45.0 ? firstAxis : secondAxis).add(road.getId());
        }
        List<SignalPhase> phases = new ArrayList<>();
        phases.add(new SignalPhase(firstAxis, DEFAULT_GREEN_SECONDS));
        if (!secondAxis.isEmpty()) phases.add(new SignalPhase(secondAxis, DEFAULT_GREEN_SECONDS));
        return new TrafficSignal(junction.intersectionId(), phases);
    }

    private static double approachBearing(Road road, RoadNetwork network) {
        if (road.getGeometry().size() >= 2) {
            return PolylineGeometry.bearingAt(road.getGeometry(), 1.0);
        }
        Intersection from = network.getIntersection(road.getStartIntersectionId());
        Intersection to = network.getIntersection(road.getEndIntersectionId());
        if (from != null && to != null && from.getLatitude() != null && to.getLatitude() != null) {
            return PolylineGeometry.bearingBetween(
                    new GeoPoint(from.getLatitude(), from.getLongitude()),
                    new GeoPoint(to.getLatitude(), to.getLongitude()));
        }
        if (from == null || to == null) return 0.0;
        double east = to.getX() - from.getX();
        double north = to.getY() - from.getY();
        return (Math.toDegrees(Math.atan2(east, north)) + 360.0) % 360.0;
    }
}
