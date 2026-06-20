package team.traffic.routing;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.*;

import java.util.*;

public final class RouteBuilder {

    private RouteBuilder() {}

    public static double calculateEdgeWeight(Road road, RoutePreference preference) {
        return switch (preference) {
            case SHORTEST_TIME -> road.getDistance() / road.getSpeedLimit();
            case CONGESTION_AWARE -> road.calculateTravelCost();
            case MINIMUM_HOPS -> 1.0;
            default -> road.getDistance();
        };
    }

    public static boolean isRoadUsable(Road road) {
        if (!road.isAvailable()) return false;
        RoadStatus status = road.getRoadCondition().getRoadStatus();
        return status != RoadStatus.BLOCKED && status != RoadStatus.CLOSED;
    }

    public static Route reconstructRoute(
            RoadNetwork network,
            String startId,
            String destId,
            Map<String, String> prev,
            Map<String, String> prevRoad,
            RoutePreference preference) {

        List<RouteStep> steps = new ArrayList<>();
        double totalDistance = 0;
        double totalTime = 0;
        double totalCost = 0;

        String current = destId;
        while (!current.equals(startId)) {
            String from = prev.get(current);
            String roadId = prevRoad.get(current);

            Road road = network.findOutgoingRoads(from).stream()
                    .filter(r -> r.getId().equals(roadId))
                    .findFirst()
                    .orElseThrow();

            double weight = calculateEdgeWeight(road, preference);
            steps.add(new RouteStep(from, current, roadId, weight));

            totalDistance += road.getDistance();
            totalTime += road.getDistance() / road.getSpeedLimit();
            totalCost += weight;

            current = from;
        }

        Collections.reverse(steps);
        return new Route(steps, totalDistance, totalTime, totalCost);
    }
}
