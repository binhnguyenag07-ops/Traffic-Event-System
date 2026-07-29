package team.traffic.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PriorityQueue;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;

/** Shared movement-aware search used by the legacy strategy facades. */
final class EdgeStateSearch {
    enum Mode {
        BFS,
        DIJKSTRA,
        ASTAR
    }

    private EdgeStateSearch() {}

    static RouteResult find(
            RoadNetwork network,
            RouteRequest request,
            String algorithmName,
            double congestionThreshold,
            Mode mode,
            Heuristic heuristic) {
        return find(
                network,
                request,
                algorithmName,
                congestionThreshold,
                mode,
                heuristic,
                true,
                System.currentTimeMillis(),
                0);
    }

    private static RouteResult find(
            RoadNetwork network,
            RouteRequest request,
            String algorithmName,
            double congestionThreshold,
            Mode mode,
            Heuristic heuristic,
            boolean allowCongestedFallback,
            long started,
            int previouslyVisited) {
        String startId = request.getStartIntersectionId();
        String destinationId = request.getDestinationIntersectionId();
        if (startId.equals(destinationId)) {
            return new RouteResult(
                    new Route(List.of(), 0.0, 0.0, 0.0),
                    algorithmName,
                    System.currentTimeMillis() - started,
                    0);
        }

        double maximumSpeed = network.getRoads().values().stream()
                .mapToDouble(Road::getSpeedLimit)
                .max()
                .orElse(1.0);
        State start = new State(startId, request.getIncomingRoadId());
        Map<State, Double> costs = new HashMap<>();
        Map<State, Previous> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::priority)
                        .thenComparing(entry -> entry.state().junctionId())
                        .thenComparing(entry -> String.valueOf(entry.state().incomingRoadId())));
        costs.put(start, 0.0);
        queue.add(new QueueEntry(start, 0.0, 0.0));
        State destination = null;
        int visited = 0;

        while (!queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            State current = entry.state();
            if (entry.cost() > costs.getOrDefault(current, Double.POSITIVE_INFINITY) + 1e-12) {
                continue;
            }
            visited++;
            if (current.junctionId().equals(destinationId)) {
                destination = current;
                break;
            }
            for (JunctionMovement movement : network.getMovementGraph().movementsFrom(
                    network,
                    current.junctionId(),
                    current.incomingRoadId(),
                    request.getVehicleType())) {
                Road road = network.getRoad(movement.toRoadId());
                if (road == null || RouteBuilder.isRoadAvoidable(road, congestionThreshold)) continue;
                double edgeCost = mode == Mode.BFS
                        ? 1.0
                        : RouteBuilder.calculateEdgeWeight(road, request.getPreference())
                                + movementPenalty(movement, request.getPreference());
                State next = new State(road.getEndIntersectionId(), road.getId());
                double candidate = entry.cost() + edgeCost;
                if (candidate + 1e-12 < costs.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    costs.put(next, candidate);
                    previous.put(next, new Previous(current, road, movement, edgeCost));
                    double priority = candidate + (mode == Mode.ASTAR
                            ? heuristicCost(
                                    network,
                                    next.junctionId(),
                                    destinationId,
                                    maximumSpeed,
                                    heuristic)
                            : 0.0);
                    queue.add(new QueueEntry(next, candidate, priority));
                }
            }
        }
        if (destination == null) {
            if (allowCongestedFallback && congestionThreshold < 1.0) {
                // Congestion avoidance is a preference, not a topological
                // closure. If every legal route is saturated, return a route
                // through that queue and let microscopic admission hold the
                // vehicle at the stop line/boundary instead of rejecting the
                // trip as impossible.
                return find(
                        network,
                        request,
                        algorithmName,
                        1.0,
                        mode,
                        heuristic,
                        false,
                        started,
                        previouslyVisited + visited);
            }
            throw new NoSuchElementException(
                    "No route found from " + startId + " to " + destinationId);
        }

        List<Previous> edges = new ArrayList<>();
        for (State cursor = destination; !cursor.equals(start); ) {
            Previous edge = previous.get(cursor);
            if (edge == null) throw new NoSuchElementException("Broken route predecessor chain.");
            edges.add(edge);
            cursor = edge.state();
        }
        Collections.reverse(edges);
        List<RouteStep> steps = new ArrayList<>();
        double totalDistance = 0.0;
        double totalTimeHours = 0.0;
        double totalCost = 0.0;
        for (Previous edge : edges) {
            Road road = edge.road();
            steps.add(new RouteStep(
                    road.getStartIntersectionId(),
                    road.getEndIntersectionId(),
                    road.getId(),
                    edge.edgeCost()));
            totalDistance += road.getDistance();
            totalTimeHours += road.getFreeFlowTravelTimeHours();
            totalCost += edge.edgeCost();
        }
        return new RouteResult(
                new Route(steps, totalDistance, totalTimeHours, totalCost),
                algorithmName,
                System.currentTimeMillis() - started,
                previouslyVisited + visited);
    }

    private static double movementPenalty(
            JunctionMovement movement,
            RoutePreference preference) {
        if (movement.penaltySeconds() == 0) return 0.0;
        return switch (preference) {
            case MINIMUM_HOPS -> 1.0;
            case SHORTEST_DISTANCE -> movement.penaltySeconds() / 3_600.0;
            case SHORTEST_TIME, CONGESTION_AWARE -> movement.penaltySeconds() / 3_600.0;
        };
    }

    private static double heuristicCost(
            RoadNetwork network,
            String fromId,
            String destinationId,
            double maximumSpeed,
            Heuristic heuristic) {
        if (heuristic == null) return 0.0;
        Intersection from = network.getIntersection(fromId);
        Intersection destination = network.getIntersection(destinationId);
        if (from == null || destination == null) return 0.0;
        return Math.max(0.0, heuristic.estimate(from, destination)) / maximumSpeed;
    }

    private record State(String junctionId, String incomingRoadId) {}
    private record QueueEntry(State state, double cost, double priority) {
        private QueueEntry(State state, double cost, double priority) {
            this.state = state;
            this.cost = cost;
            this.priority = priority == 0.0 ? cost : priority;
        }
    }
    private record Previous(
            State state,
            Road road,
            JunctionMovement movement,
            double edgeCost) {}
}
