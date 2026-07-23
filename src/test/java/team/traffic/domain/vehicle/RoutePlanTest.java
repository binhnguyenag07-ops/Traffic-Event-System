package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
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

    private static Route routeAToD() {
        return new Route(
                List.of(
                        new RouteStep("A", "B", "AB", 1.0),
                        new RouteStep("B", "C", "BC", 1.0),
                        new RouteStep("C", "D", "CD", 1.0)),
                3.0, 3.0, 3.0);
    }
}
