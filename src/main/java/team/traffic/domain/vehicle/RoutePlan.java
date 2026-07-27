package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import team.traffic.domain.network.JunctionMovementGraph;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteAnchor;
import team.traffic.domain.routing.RouteCandidate;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteReplacementResult;
import team.traffic.domain.routing.RouteStep;

/**
 * The route a vehicle is following and how far through it the vehicle is.
 *
 * <p>A route on its own says nothing about progress, and an index on its own
 * has nothing to index into. Keeping them apart meant every question about the
 * journey had to re-establish that both existed and that the index was in
 * range, so the same guard —
 * {@code route == null || index + n >= route.getStepCount()} — was written out
 * four times, once per question asked.</p>
 *
 * <p>{@link #stepAt(int)} asks all four of those questions with one bounds
 * check, returning {@code null} for a step that is not there rather than
 * throwing. Reassigning a route resets the progress in the same call, so the
 * two cannot disagree.</p>
 */
public final class RoutePlan {
    private Route route;
    private int nextStepIndex;
    private RoutePreference preference = RoutePreference.CONGESTION_AWARE;
    private String lastAlgorithmName;
    private long version;

    /** Takes a new route and starts again from its first step. */
    public void assign(Route route, String algorithmName) {
        this.route = route;
        this.nextStepIndex = 0;
        this.lastAlgorithmName = algorithmName;
        version++;
    }

    /** Discards the route, leaving the vehicle with nowhere to go. */
    public void clear() {
        if (route == null && nextStepIndex == 0) {
            return;
        }
        this.route = null;
        this.nextStepIndex = 0;
        version++;
    }

    /**
     * Step {@code offset} places from the one the vehicle is about to take, or
     * {@code null} when there is no such step.
     *
     * <p>Offset zero is the step being taken now, {@code 1} the one after it,
     * and {@code -1} the road just left.</p>
     */
    public RouteStep stepAt(int offset) {
        if (route == null) {
            return null;
        }
        int index = nextStepIndex + offset;
        if (index < 0 || index >= route.getStepCount()) {
            return null;
        }
        return route.getSteps().get(index);
    }

    /** The step the vehicle is about to take, or {@code null} at the end. */
    public RouteStep currentStep() {
        return stepAt(0);
    }

    /** Marks the current step as taken. */
    public void advance() {
        nextStepIndex++;
        version++;
    }

    /** Whether every step has been taken. */
    public boolean isExhausted() {
        return route == null || nextStepIndex >= route.getStepCount();
    }

    public Route getRoute() {
        return route;
    }

    public int getNextStepIndex() {
        return nextStepIndex;
    }

    public RoutePreference getPreference() {
        return preference;
    }

    public void setPreference(RoutePreference preference) {
        if (preference == null) {
            throw new IllegalArgumentException("Route preference must not be null.");
        }
        this.preference = preference;
    }

    /** Name of the algorithm that produced the current route, for statistics. */
    public String getLastAlgorithmName() {
        return lastAlgorithmName;
    }

    public long getVersion() {
        return version;
    }

    /**
     * Validates a candidate completely before replacing the remaining suffix.
     *
     * <p>The occupied step is retained for an on-road anchor. At a junction,
     * only already-completed steps are retained.</p>
     */
    public RouteReplacementResult replaceRemaining(
            RouteAnchor expectedAnchor,
            RouteCandidate candidate,
            JunctionMovementGraph movements) {
        Objects.requireNonNull(movements, "Movement graph is required.");
        return replaceRemaining(
                expectedAnchor,
                candidate,
                movements.attachedNetwork(),
                movements,
                VehicleType.CAR,
                candidate.route().isEmpty()
                        ? anchorJunction(expectedAnchor)
                        : candidate.route().getSteps()
                                .get(candidate.route().getStepCount() - 1)
                                .getToIntersectionId());
    }

    public RouteReplacementResult replaceRemaining(
            RouteAnchor expectedAnchor,
            RouteCandidate candidate,
            RoadNetwork network,
            JunctionMovementGraph movements,
            VehicleType vehicleType,
            String destinationIntersectionId) {
        Objects.requireNonNull(expectedAnchor, "Route anchor is required.");
        Objects.requireNonNull(candidate, "Route candidate is required.");
        Objects.requireNonNull(network, "Road network is required.");
        Objects.requireNonNull(movements, "Movement graph is required.");
        Objects.requireNonNull(vehicleType, "Vehicle type is required.");

        if (candidate.sourceRouteVersion() != version) {
            return rejected(RouteReplacementResult.Status.REJECTED_STALE, "Route version changed.");
        }
        String anchorJunction = anchorJunction(expectedAnchor);
        List<RouteStep> suffix = candidate.route().getSteps();
        if (suffix.isEmpty() && !anchorJunction.equals(destinationIntersectionId)) {
            return rejected(RouteReplacementResult.Status.REJECTED_DESTINATION,
                    "An empty suffix is valid only when the anchor is the destination.");
        }
        if (!suffix.isEmpty() && !anchorJunction.equals(suffix.get(0).getFromIntersectionId())) {
            return rejected(RouteReplacementResult.Status.REJECTED_ANCHOR,
                    "Candidate does not start at the current route anchor.");
        }
        if (!suffix.isEmpty()
                && !suffix.get(suffix.size() - 1).getToIntersectionId()
                        .equals(destinationIntersectionId)) {
            return rejected(RouteReplacementResult.Status.REJECTED_DESTINATION,
                    "Candidate destination does not match the active trip.");
        }

        String incomingRoadId = incomingRoadId(expectedAnchor);
        String junctionId = anchorJunction;
        for (RouteStep step : suffix) {
            Road road = network.getRoad(step.getRoadId());
            if (road == null
                    || !junctionId.equals(step.getFromIntersectionId())
                    || !junctionId.equals(road.getStartIntersectionId())
                    || !step.getToIntersectionId().equals(road.getEndIntersectionId())) {
                return rejected(RouteReplacementResult.Status.REJECTED_DISCONNECTED,
                        "Candidate contains a missing or disconnected road.");
            }
            if (!movements.isAllowed(
                    network, junctionId, incomingRoadId, road.getId(), vehicleType)) {
                return rejected(RouteReplacementResult.Status.REJECTED_ILLEGAL_MOVEMENT,
                        "Candidate contains a forbidden junction movement.");
            }
            incomingRoadId = road.getId();
            junctionId = road.getEndIntersectionId();
        }

        int prefixEnd = expectedAnchor instanceof RouteAnchor.OnRoad
                ? nextStepIndex + 1
                : nextStepIndex;
        List<RouteStep> combined = new ArrayList<>();
        if (route != null && prefixEnd > 0) {
            combined.addAll(route.getSteps().subList(0, Math.min(prefixEnd, route.getStepCount())));
        }
        combined.addAll(suffix);

        double distance = 0.0;
        double time = 0.0;
        double cost = 0.0;
        for (RouteStep step : combined) {
            Road road = network.getRoad(step.getRoadId());
            distance += road.getDistance();
            time += road.getDistance() / road.getSpeedLimit();
            cost += step.getCost();
        }
        this.route = new Route(combined, distance, time, cost);
        this.lastAlgorithmName = candidate.algorithmName();
        version++;
        return new RouteReplacementResult(
                RouteReplacementResult.Status.REPLACED,
                "Route suffix replaced atomically.",
                version);
    }

    private RouteReplacementResult rejected(
            RouteReplacementResult.Status status,
            String reason) {
        return new RouteReplacementResult(status, reason, version);
    }

    private String anchorJunction(RouteAnchor anchor) {
        if (anchor instanceof RouteAnchor.AtJunction atJunction) {
            return atJunction.junctionId();
        }
        return ((RouteAnchor.OnRoad) anchor).exitJunctionId();
    }

    private String incomingRoadId(RouteAnchor anchor) {
        if (anchor instanceof RouteAnchor.AtJunction atJunction) {
            return atJunction.incomingRoadId().orElse(null);
        }
        return ((RouteAnchor.OnRoad) anchor).roadId();
    }
}
