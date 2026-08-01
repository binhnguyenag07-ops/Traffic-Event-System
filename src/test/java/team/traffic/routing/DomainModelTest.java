package team.traffic.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainModelTest {

    // ── Intersection ─────────────────────────────────────

    @Nested
    @DisplayName("Intersection")
    class IntersectionTests {

        @Test
        @DisplayName("valid construction")
        void validConstruction() {
            Intersection i = new Intersection("A", "Main St", 10.5, 20.3);
            assertThat(i.getId()).isEqualTo("A");
            assertThat(i.getName()).isEqualTo("Main St");
            assertThat(i.getX()).isEqualTo(10.5);
            assertThat(i.getY()).isEqualTo(20.3);
        }

        @Test
        @DisplayName("null id throws")
        void nullId() {
            assertThatThrownBy(() -> new Intersection(null, "name", 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank id throws")
        void blankId() {
            assertThatThrownBy(() -> new Intersection("  ", "name", 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("empty id throws")
        void emptyId() {
            assertThatThrownBy(() -> new Intersection("", "name", 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("null name throws")
        void nullName() {
            assertThatThrownBy(() -> new Intersection("A", null, 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank name throws")
        void blankName() {
            assertThatThrownBy(() -> new Intersection("A", "  ", 0, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("zero coordinates are valid")
        void zeroCoordinates() {
            Intersection i = new Intersection("A", "A", 0, 0);
            assertThat(i.getX()).isEqualTo(0.0);
            assertThat(i.getY()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("negative coordinates are valid")
        void negativeCoordinates() {
            Intersection i = new Intersection("A", "A", -10.5, -20.3);
            assertThat(i.getX()).isEqualTo(-10.5);
            assertThat(i.getY()).isEqualTo(-20.3);
        }
    }

    // ── Road ─────────────────────────────────────────────

    @Nested
    @DisplayName("Road")
    class RoadTests {

        @Test
        @DisplayName("valid construction")
        void validConstruction() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 5, null);
            assertThat(road.getId()).isEqualTo("R1");
            assertThat(road.getStartIntersectionId()).isEqualTo("A");
            assertThat(road.getEndIntersectionId()).isEqualTo("B");
            assertThat(road.getDistance()).isEqualTo(10.0);
            assertThat(road.getSpeedLimit()).isEqualTo(50.0);
            assertThat(road.getCapacity()).isEqualTo(100);
            assertThat(road.getCurrentVehicleCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("null condition defaults to open")
        void nullCondition() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0, null);
            assertThat(road.getCondition()).isNotNull();
            assertThat(road.getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        }

        @Test
        @DisplayName("null id throws")
        void nullId() {
            assertThatThrownBy(() -> new Road(null, "A", "B", 10, 50, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank id throws")
        void blankId() {
            assertThatThrownBy(() -> new Road("  ", "A", "B", 10, 50, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank start intersection throws")
        void blankStart() {
            assertThatThrownBy(() -> new Road("R1", "  ", "B", 10, 50, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("blank end intersection throws")
        void blankEnd() {
            assertThatThrownBy(() -> new Road("R1", "A", "  ", 10, 50, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("negative distance throws")
        void negativeDistance() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", -1, 50, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("zero distance throws")
        void zeroDistance() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", 0, 50, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("negative speed limit throws")
        void negativeSpeedLimit() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", 10, -1, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("zero speed limit throws")
        void zeroSpeedLimit() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", 10, 0, 100, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("zero capacity throws")
        void zeroCapacity() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", 10, 50, 0, 0, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("negative vehicle count throws")
        void negativeVehicleCount() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", 10, 50, 100, -1, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("vehicle count exceeds capacity throws")
        void vehicleCountExceedsCapacity() {
            assertThatThrownBy(() -> new Road("R1", "A", "B", 10, 50, 5, 6, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("set condition to null throws")
        void setConditionNull() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0, null);
            assertThatThrownBy(() -> road.setCondition(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("set vehicle count within capacity")
        void setVehicleCount() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0, null);
            road.setCurrentVehicleCount(50);
            assertThat(road.getCurrentVehicleCount()).isEqualTo(50);
        }

        @Test
        @DisplayName("set vehicle count exceeds capacity throws")
        void setVehicleCountExceedsCapacity() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0, null);
            assertThatThrownBy(() -> road.setCurrentVehicleCount(101))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("calculateTravelCost for open road")
        void calculateTravelCostOpen() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.OPEN));
            double cost = road.calculateTravelCost();
            // base = 10/50 = 0.2, cost = 0.2 * 1 * 1 = 0.2
            assertThat(cost).isEqualTo(0.2);
        }

        @Test
        @DisplayName("calculateTravelCost for blocked road returns infinity")
        void calculateTravelCostBlocked() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.BLOCKED));
            assertThat(road.calculateTravelCost()).isEqualTo(Double.POSITIVE_INFINITY);
        }

        @Test
        @DisplayName("isAvailable for open road")
        void isAvailableOpen() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.OPEN));
            assertThat(road.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("isAvailable for blocked road")
        void isAvailableBlocked() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.BLOCKED));
            assertThat(road.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("isAvailable for closed road")
        void isAvailableClosed() {
            Road road = new Road("R1", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.CLOSED));
            assertThat(road.isAvailable()).isFalse();
        }

        @Test
        @DisplayName("isAvailable for road at capacity")
        void isAvailableAtCapacity() {
            Road road = new Road("R1", "A", "B", 10, 50, 5, 5,
                    new RoadCondition(0, 0, RoadStatus.OPEN));
            assertThat(road.isAvailable()).isFalse();
        }
    }

    // ── RoadCondition ────────────────────────────────────

    @Nested
    @DisplayName("RoadCondition")
    class RoadConditionTests {

        @Test
        @DisplayName("valid construction")
        void validConstruction() {
            RoadCondition rc = new RoadCondition(0.5, 0.3, RoadStatus.CONGESTED);
            assertThat(rc.getCongestionLevel()).isEqualTo(0.5);
            assertThat(rc.getVehicleDensity()).isEqualTo(0.3);
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        }

        @Test
        @DisplayName("null status defaults to OPEN")
        void nullStatus() {
            RoadCondition rc = new RoadCondition(0, 0, null);
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        }

        @Test
        @DisplayName("negative congestion clamps to 0")
        void negativeCongestion() {
            RoadCondition rc = new RoadCondition(-0.5, 0, RoadStatus.OPEN);
            assertThat(rc.getCongestionLevel()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("congestion above 1 clamps to 1")
        void congestionAboveOne() {
            RoadCondition rc = new RoadCondition(1.5, 0, RoadStatus.OPEN);
            assertThat(rc.getCongestionLevel()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("negative density clamps to 0")
        void negativeDensity() {
            RoadCondition rc = new RoadCondition(0, -0.5, RoadStatus.OPEN);
            assertThat(rc.getVehicleDensity()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("density above 1 clamps to 1")
        void densityAboveOne() {
            RoadCondition rc = new RoadCondition(0, 1.5, RoadStatus.OPEN);
            assertThat(rc.getVehicleDensity()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("increaseCongestion adds to current level")
        void increaseCongestion() {
            RoadCondition rc = new RoadCondition(0.2, 0, RoadStatus.OPEN);
            double result = rc.increaseCongestion(0.3);
            assertThat(result).isCloseTo(0.5, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("increaseCongestion negative amount throws")
        void increaseCongestionNegative() {
            RoadCondition rc = new RoadCondition(0.2, 0, RoadStatus.OPEN);
            assertThatThrownBy(() -> rc.increaseCongestion(-0.1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("increaseCongestion changes OPEN to CONGESTED")
        void increaseCongestionChangesStatus() {
            RoadCondition rc = new RoadCondition(0, 0, RoadStatus.OPEN);
            rc.increaseCongestion(0.1);
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        }

        @Test
        @DisplayName("increaseCongestion clamps to 1.0")
        void increaseCongestionClamps() {
            RoadCondition rc = new RoadCondition(0.9, 0, RoadStatus.OPEN);
            rc.increaseCongestion(0.5);
            assertThat(rc.getCongestionLevel()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("markBlocked sets BLOCKED and congestion to 1")
        void markBlocked() {
            RoadCondition rc = new RoadCondition(0, 0, RoadStatus.OPEN);
            rc.markBlocked();
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
            assertThat(rc.getCongestionLevel()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("markOpen clears status and congestion")
        void markOpen() {
            RoadCondition rc = new RoadCondition(0.8, 0.5, RoadStatus.CONGESTED);
            rc.markOpen();
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.OPEN);
            assertThat(rc.getCongestionLevel()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("setCongestionLevel changes status OPEN->CONGESTED")
        void setCongestionLevel() {
            RoadCondition rc = new RoadCondition(0, 0, RoadStatus.OPEN);
            rc.setCongestionLevel(0.5);
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        }

        @Test
        @DisplayName("setCongestionLevel to 0 changes CONGESTED->OPEN")
        void setCongestionLevelToZero() {
            RoadCondition rc = new RoadCondition(0.5, 0, RoadStatus.CONGESTED);
            rc.setCongestionLevel(0);
            assertThat(rc.getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        }

        @Test
        @DisplayName("setRoadStatus null throws")
        void setRoadStatusNull() {
            RoadCondition rc = new RoadCondition(0, 0, RoadStatus.OPEN);
            assertThatThrownBy(() -> rc.setRoadStatus(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // ── Route / RouteStep / RouteRequest / RouteResult ────

    @Nested
    @DisplayName("Routing domain models")
    class RoutingDomainTests {

        @Test
        @DisplayName("Route construction and getters")
        void routeConstruction() {
            RouteStep step = new RouteStep("A", "B", "AB", 5.0);
            Route route = new Route(List.of(step), 10.0, 0.2, 10.0);

            assertThat(route.getSteps()).hasSize(1);
            assertThat(route.getTotalDistance()).isEqualTo(10.0);
            assertThat(route.getEstimatedTravelTime()).isEqualTo(0.2);
            assertThat(route.getTotalCost()).isEqualTo(10.0);
            assertThat(route.getStepCount()).isEqualTo(1);
            assertThat(route.isEmpty()).isFalse();
        }

        @Test
        @DisplayName("Route empty when no steps")
        void routeEmpty() {
            Route route = new Route(Collections.emptyList(), 0, 0, 0);
            assertThat(route.isEmpty()).isTrue();
            assertThat(route.getStepCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("Route steps are unmodifiable")
        void routeStepsUnmodifiable() {
            Route route = new Route(List.of(new RouteStep("A", "B", "AB", 5.0)), 5, 0.1, 5);
            assertThatThrownBy(() -> route.getSteps().add(new RouteStep("B", "C", "BC", 5.0)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("Route toString")
        void routeToString() {
            Route route = new Route(List.of(new RouteStep("A", "B", "AB", 5.0)), 5, 0.1, 5);
            assertThat(route.toString()).contains("Route{steps=1");
        }

        @Test
        @DisplayName("RouteStep construction and getters")
        void routeStepConstruction() {
            RouteStep step = new RouteStep("A", "B", "AB", 5.0);
            assertThat(step.getFromIntersectionId()).isEqualTo("A");
            assertThat(step.getToIntersectionId()).isEqualTo("B");
            assertThat(step.getRoadId()).isEqualTo("AB");
            assertThat(step.getCost()).isEqualTo(5.0);
        }

        @Test
        @DisplayName("RouteStep toString")
        void routeStepToString() {
            RouteStep step = new RouteStep("A", "B", "AB", 5.0);
            assertThat(step.toString()).contains("A -> B");
        }

        @Test
        @DisplayName("RouteRequest construction and getters")
        void routeRequestConstruction() {
            RouteRequest req = new RouteRequest("A", "B", RoutePreference.SHORTEST_DISTANCE);
            assertThat(req.getStartIntersectionId()).isEqualTo("A");
            assertThat(req.getDestinationIntersectionId()).isEqualTo("B");
            assertThat(req.getPreference()).isEqualTo(RoutePreference.SHORTEST_DISTANCE);
        }

        @Test
        @DisplayName("RouteRequest toString")
        void routeRequestToString() {
            RouteRequest req = new RouteRequest("A", "B", RoutePreference.SHORTEST_TIME);
            assertThat(req.toString()).contains("A -> B");
        }

        @Test
        @DisplayName("RouteResult construction and getters")
        void routeResultConstruction() {
            Route route = new Route(List.of(new RouteStep("A", "B", "AB", 5.0)), 5, 0.1, 5);
            RouteResult result = new RouteResult(route, "Dijkstra", 10, 5);

            assertThat(result.getRoute()).isEqualTo(route);
            assertThat(result.getAlgorithmName()).isEqualTo("Dijkstra");
            assertThat(result.getComputationTimeMillis()).isEqualTo(10);
            assertThat(result.getVisitedNodes()).isEqualTo(5);
        }

        @Test
        @DisplayName("RouteResult toString")
        void routeResultToString() {
            Route route = new Route(List.of(new RouteStep("A", "B", "AB", 5.0)), 5, 0.1, 5);
            RouteResult result = new RouteResult(route, "BFS", 1, 3);
            assertThat(result.toString()).contains("BFS");
        }
    }
}
