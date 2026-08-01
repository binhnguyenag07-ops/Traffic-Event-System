package team.traffic.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.PriorityQueue;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.AdaptiveRouteRequest;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteAnchor;
import team.traffic.domain.routing.RouteCandidate;
import team.traffic.domain.routing.RouteCost;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.RoadAdvisory;
import team.traffic.domain.vehicle.navigation.UTurnPolicy;

/** Edge-state Dijkstra search over {@code (junction, incoming road)} nodes. */
public final class AdaptiveRouter {
    public static final String ALGORITHM_NAME = "AdaptiveEdgeDijkstra";

    public RouteCandidate findRoute(
            RoadNetwork network,
            RoutingSnapshot snapshot,
            AdaptiveRouteRequest request) {
        if (network == null || snapshot == null || request == null) {
            throw new IllegalArgumentException("Network, routing snapshot, and request are required.");
        }
        State start = stateOf(request.anchor());
        if (start.junctionId().equals(request.destinationIntersectionId())) {
            Route route = new Route(List.of(), 0.0, 0.0, 0.0);
            return candidate(
                    route,
                    Optional.empty(),
                    request.sourceRouteVersion(),
                    0.0,
                    0.0,
                    0.0);
        }

        Map<State, Double> distance = new HashMap<>();
        Map<State, Previous> previous = new HashMap<>();
        PriorityQueue<QueueEntry> queue = new PriorityQueue<>(
                Comparator.comparingDouble(QueueEntry::cost)
                        .thenComparing(entry -> entry.state().junctionId())
                        .thenComparing(entry -> String.valueOf(entry.state().incomingRoadId())));
        distance.put(start, 0.0);
        queue.add(new QueueEntry(start, 0.0));
        State destinationState = null;

        while (!queue.isEmpty()) {
            QueueEntry entry = queue.poll();
            State current = entry.state();
            if (entry.cost() > distance.getOrDefault(current, Double.POSITIVE_INFINITY)) continue;
            if (current.junctionId().equals(request.destinationIntersectionId())) {
                destinationState = current;
                break;
            }
            for (JunctionMovement movement : network.getMovementGraph().movementsFrom(
                    network,
                    current.junctionId(),
                    current.incomingRoadId(),
                    request.vehicleType())) {
                if (current.equals(start)
                        && request.requireUTurnAsFirstMovement()
                        && !movement.isUTurn()) {
                    continue;
                }
                if (!uTurnAllowed(movement, request)) continue;
                Road road = network.getRoad(movement.toRoadId());
                if (road == null || request.temporarilyAvoidedRoadIds().contains(road.getId())) continue;
                RoutingSnapshot.RoadState roadState = snapshot.road(road.getId());
                if (roadState == null || knownHardBlock(road.getId(), snapshot, request)) continue;
                double expectedRoadSeconds = roadState.marginalTravelTimeSeconds();
                if (roadState.temporarilyFull()) {
                    expectedRoadSeconds += Math.max(
                            10.0, roadState.travelTimeSeconds() * 0.5);
                }
                double uncertaintySeconds = (1.0 - roadState.confidence()) * 5.0;
                double edgeCost = searchWeight(
                        road,
                        roadState,
                        movement,
                        request,
                        expectedRoadSeconds,
                        uncertaintySeconds);
                State next = new State(road.getEndIntersectionId(), road.getId());
                double candidateCost = entry.cost() + edgeCost;
                if (candidateCost + 1e-9 < distance.getOrDefault(next, Double.POSITIVE_INFINITY)) {
                    distance.put(next, candidateCost);
                    previous.put(next, new Previous(
                            current,
                            movement,
                            road,
                            edgeCost,
                            expectedRoadSeconds,
                            uncertaintySeconds));
                    queue.add(new QueueEntry(next, candidateCost));
                }
            }
        }

        if (destinationState == null) {
            throw new NoSuchElementException("No adaptive route found for " + request.vehicleId());
        }
        List<Previous> reversed = new ArrayList<>();
        State cursor = destinationState;
        while (!cursor.equals(start)) {
            Previous edge = previous.get(cursor);
            if (edge == null) throw new NoSuchElementException("Broken adaptive route predecessor chain.");
            reversed.add(edge);
            cursor = edge.state();
        }
        Collections.reverse(reversed);

        List<RouteStep> steps = new ArrayList<>();
        double distanceKm = 0.0;
        double travelSeconds = 0.0;
        double manoeuvrePenalty = 0.0;
        double uncertaintySeconds = 0.0;
        for (Previous edge : reversed) {
            Road road = edge.road();
            steps.add(new RouteStep(
                    road.getStartIntersectionId(),
                    road.getEndIntersectionId(),
                    road.getId(),
                    edge.edgeCostSeconds()));
            distanceKm += road.getDistance();
            travelSeconds += edge.expectedRoadSeconds();
            manoeuvrePenalty += edge.movement().penaltySeconds();
            uncertaintySeconds += edge.uncertaintySeconds();
        }
        Route route = new Route(
                steps,
                distanceKm,
                travelSeconds / 3_600.0,
                distance.get(destinationState));
        double committedRoadSeconds = committedRoadSeconds(network, snapshot, request.anchor());
        Optional<JunctionMovement> first = reversed.isEmpty()
                ? Optional.empty()
                : Optional.of(reversed.get(0).movement());
        return candidate(
                route,
                first,
                request.sourceRouteVersion(),
                committedRoadSeconds,
                manoeuvrePenalty,
                uncertaintySeconds);
    }

    private RouteCandidate candidate(
            Route route,
            Optional<JunctionMovement> firstMovement,
            long routeVersion,
            double committedRoadSeconds,
            double manoeuvrePenaltySeconds,
            double uncertaintySeconds) {
        return new RouteCandidate(
                route,
                new RouteCost(
                        route.getEstimatedTravelTime() * 3_600.0 + committedRoadSeconds,
                        route.getTotalDistance(),
                        manoeuvrePenaltySeconds,
                        uncertaintySeconds),
                firstMovement,
                RouteCandidate.fingerprintOf(route),
                ALGORITHM_NAME,
                routeVersion);
    }

    private double searchWeight(
            Road road,
            RoutingSnapshot.RoadState roadState,
            JunctionMovement movement,
            AdaptiveRouteRequest request,
            double expectedRoadSeconds,
            double uncertaintySeconds) {
        double movementPenalty = movement.penaltySeconds();
        if (request.vehicleType()
                == team.traffic.domain.vehicle.VehicleType.EMERGENCY
                && movement.isUTurn()) {
            movementPenalty *= 0.2;
        }
        return switch (request.preference()) {
            case MINIMUM_HOPS -> 1.0 + (movementPenalty > 0 ? 1.0 : 0.0);
            case SHORTEST_DISTANCE ->
                    road.getDistance() + movementPenalty / 3_600.0;
            case SHORTEST_TIME ->
                    road.getFreeFlowTravelTimeSeconds() + movementPenalty;
            case CONGESTION_AWARE ->
                    expectedRoadSeconds + movementPenalty + uncertaintySeconds;
        };
    }

    private boolean knownHardBlock(
            String roadId,
            RoutingSnapshot snapshot,
            AdaptiveRouteRequest request) {
        boolean driverKnows = request.knowledge().forRoad(
                        roadId, snapshot.capturedAtSeconds()).stream()
                .map(RoadAdvisory::kind)
                .anyMatch(kind -> kind == AdvisoryKind.CLOSED || kind == AdvisoryKind.LANES_BLOCKED);
        RoutingSnapshot.RoadState state = snapshot.road(roadId);
        if (state.networkRoutable()) return false;
        return !state.dynamicallyAffected() || driverKnows;
    }

    private boolean uTurnAllowed(
            JunctionMovement movement,
            AdaptiveRouteRequest request) {
        if (!movement.isUTurn()) return true;
        if (request.uTurnPolicy() == UTurnPolicy.NEVER) return false;
        return request.uTurnPolicy() == UTurnPolicy.WHEN_MATERIALLY_BETTER
                || request.reason() == team.traffic.domain.routing.RerouteReason.KNOWN_HARD_BLOCK
                || request.reason() == team.traffic.domain.routing.RerouteReason.STALL_TIMEOUT
                || request.reason() == team.traffic.domain.routing.RerouteReason.DEADLOCK_RECOVERY;
    }

    private State stateOf(RouteAnchor anchor) {
        if (anchor instanceof RouteAnchor.AtJunction at) {
            return new State(at.junctionId(), at.incomingRoadId().orElse(null));
        }
        RouteAnchor.OnRoad onRoad = (RouteAnchor.OnRoad) anchor;
        return new State(onRoad.exitJunctionId(), onRoad.roadId());
    }

    private double committedRoadSeconds(
            RoadNetwork network,
            RoutingSnapshot snapshot,
            RouteAnchor anchor) {
        if (!(anchor instanceof RouteAnchor.OnRoad onRoad)) return 0.0;
        Road road = network.getRoad(onRoad.roadId());
        RoutingSnapshot.RoadState state = snapshot.road(onRoad.roadId());
        if (road == null || state == null) return 0.0;
        return state.travelTimeSeconds() * (1.0 - onRoad.progress());
    }

    private record State(String junctionId, String incomingRoadId) {}
    private record QueueEntry(State state, double cost) {}
    private record Previous(
            State state,
            JunctionMovement movement,
            Road road,
            double edgeCostSeconds,
            double expectedRoadSeconds,
            double uncertaintySeconds) {}
}
