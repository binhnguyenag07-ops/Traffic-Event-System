package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteAnchor;
import team.traffic.domain.routing.RouteCandidate;
import team.traffic.domain.routing.RouteCost;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteReplacementResult;
import team.traffic.domain.routing.RouteStep;

/**
 * {@code stepAt} replaced four copies of the same null-and-bounds guard, so
 * what matters is that it is safe at both ends of the route and off the end of
 * it, and that route and progress can never disagree.
 */
class RoutePlanTest {

    @Test
    void aFreshPlanHasNoRouteAndIsExhausted() {
        RoutePlan plan = new RoutePlan();

        assertThat(plan.getRoute()).isNull();
        assertThat(plan.isExhausted()).isTrue();
        assertThat(plan.currentStep()).isNull();
        assertThat(plan.getPreference()).isEqualTo(RoutePreference.CONGESTION_AWARE);
    }

    @Test
    void stepAtReachesForwardsAndBackwardsFromTheCurrentStep() {
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "DIJKSTRA");
        plan.advance();

        assertThat(plan.stepAt(-1).getRoadId()).isEqualTo("AB");
        assertThat(plan.stepAt(0).getRoadId()).isEqualTo("BC");
        assertThat(plan.stepAt(1).getRoadId()).isEqualTo("CD");
    }

    @Test
    void stepAtReturnsNullBeyondEitherEndRatherThanThrowing() {
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "DIJKSTRA");

        assertThat(plan.stepAt(-1)).as("nothing before the first step").isNull();
        assertThat(plan.stepAt(3)).as("nothing after the last").isNull();
        assertThat(plan.stepAt(99)).isNull();
    }

    @Test
    void stepAtIsSafeWithNoRouteAtAll() {
        RoutePlan plan = new RoutePlan();

        assertThat(plan.stepAt(0)).isNull();
        assertThat(plan.stepAt(1)).isNull();
        assertThat(plan.stepAt(-1)).isNull();
    }

    @Test
    void advancingRunsOutAfterTheLastStep() {
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "ASTAR");

        assertThat(plan.isExhausted()).isFalse();
        plan.advance();
        plan.advance();
        assertThat(plan.isExhausted()).isFalse();
        plan.advance();
        assertThat(plan.isExhausted()).isTrue();
        assertThat(plan.currentStep()).isNull();
    }

    @Test
    void assigningAnotherRouteRestartsProgress() {
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "BFS");
        plan.advance();
        plan.advance();

        plan.assign(routeAToD(), "DIJKSTRA");

        assertThat(plan.getNextStepIndex())
                .as("progress through the old route means nothing in the new one")
                .isZero();
        assertThat(plan.currentStep().getRoadId()).isEqualTo("AB");
        assertThat(plan.getLastAlgorithmName()).isEqualTo("DIJKSTRA");
    }

    @Test
    void clearingLeavesNoRouteAndNoProgress() {
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "BFS");
        plan.advance();

        plan.clear();

        assertThat(plan.getRoute()).isNull();
        assertThat(plan.getNextStepIndex()).isZero();
        assertThat(plan.isExhausted()).isTrue();
    }

    @Test
    void preferenceSurvivesLosingTheRoute() {
        RoutePlan plan = new RoutePlan();
        plan.setPreference(RoutePreference.SHORTEST_TIME);
        plan.assign(routeAToD(), "ASTAR");

        plan.clear();

        assertThat(plan.getPreference())
                .as("how a driver wants to be routed is not part of any one route")
                .isEqualTo(RoutePreference.SHORTEST_TIME);
    }

    @Test
    void rejectsANullPreference() {
        assertThatThrownBy(() -> new RoutePlan().setPreference(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Route preference");
    }

    @Test
    void onRoadReplacementPreservesOccupiedStepAndReplacesOnlyFutureSuffix() {
        RoadNetwork network = network();
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "DIJKSTRA");
        Route suffix = new Route(
                List.of(new RouteStep("B", "D", "BD", 1.0)),
                1.0, 0.02, 1.0);
        RouteCandidate candidate = new RouteCandidate(
                suffix,
                RouteCost.from(suffix),
                Optional.empty(),
                "BD",
                "adaptive",
                plan.getVersion());

        RouteReplacementResult result = plan.replaceRemaining(
                new RouteAnchor.OnRoad("AB", 0.4, "B"),
                candidate,
                network,
                network.getMovementGraph(),
                VehicleType.CAR,
                "D");

        assertThat(result.replaced()).isTrue();
        assertThat(plan.getNextStepIndex()).isZero();
        assertThat(plan.getRoute().getSteps())
                .extracting(RouteStep::getRoadId)
                .containsExactly("AB", "BD");
    }

    @Test
    void staleCandidateIsRejectedWithoutMutatingRoute() {
        RoadNetwork network = network();
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "DIJKSTRA");
        Route before = plan.getRoute();
        Route suffix = new Route(
                List.of(new RouteStep("B", "D", "BD", 1.0)),
                1.0, 0.02, 1.0);
        RouteCandidate stale = new RouteCandidate(
                suffix,
                RouteCost.from(suffix),
                Optional.empty(),
                "BD",
                "adaptive",
                plan.getVersion() - 1);

        RouteReplacementResult result = plan.replaceRemaining(
                new RouteAnchor.OnRoad("AB", 0.4, "B"),
                stale,
                network,
                network.getMovementGraph(),
                VehicleType.CAR,
                "D");

        assertThat(result.status())
                .isEqualTo(RouteReplacementResult.Status.REJECTED_STALE);
        assertThat(plan.getRoute()).isSameAs(before);
    }

    @Test
    void emptySuffixCannotSilentlyTruncateRouteBeforeDestination() {
        RoadNetwork network = network();
        RoutePlan plan = new RoutePlan();
        plan.assign(routeAToD(), "DIJKSTRA");
        Route before = plan.getRoute();
        Route empty = new Route(List.of(), 0.0, 0.0, 0.0);
        RouteCandidate candidate = new RouteCandidate(
                empty,
                RouteCost.from(empty),
                Optional.empty(),
                "EMPTY",
                "adaptive",
                plan.getVersion());

        RouteReplacementResult result = plan.replaceRemaining(
                new RouteAnchor.OnRoad("AB", 0.4, "B"),
                candidate,
                network,
                network.getMovementGraph(),
                VehicleType.CAR,
                "D");

        assertThat(result.status())
                .isEqualTo(RouteReplacementResult.Status.REJECTED_DESTINATION);
        assertThat(plan.getRoute()).isSameAs(before);
    }

    private static Route routeAToD() {
        return new Route(
                List.of(
                        new RouteStep("A", "B", "AB", 1.0),
                        new RouteStep("B", "C", "BC", 1.0),
                        new RouteStep("C", "D", "CD", 1.0)),
                3.0, 3.0, 3.0);
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addIntersection(new Intersection("C", "C", 2, 0));
        network.addIntersection(new Intersection("D", "D", 3, 1));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BC", "B", "C"));
        network.addRoad(road("CD", "C", "D"));
        network.addRoad(road("BD", "B", "D"));
        return network;
    }

    private static Road road(String id, String from, String to) {
        return new Road(
                id, from, to, 1.0, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
