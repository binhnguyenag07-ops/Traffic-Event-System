package team.traffic.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteStep;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteBuilderReconstructionTest {

    @Test
    @DisplayName("reconstruct 1-step route")
    void oneStepRoute() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0, null));

        Map<String, String> prev = Map.of("B", "A");
        Map<String, String> prevRoad = Map.of("B", "AB");

        Route route = RouteBuilder.reconstructRoute(network, "A", "B", prev, prevRoad, RoutePreference.SHORTEST_DISTANCE);

        assertThat(route.getStepCount()).isEqualTo(1);
        assertThat(route.getSteps().get(0).getFromIntersectionId()).isEqualTo("A");
        assertThat(route.getSteps().get(0).getToIntersectionId()).isEqualTo("B");
        assertThat(route.getSteps().get(0).getRoadId()).isEqualTo("AB");
    }

    @Test
    @DisplayName("reconstruct multi-step route preserves order")
    void multiStepRouteOrder() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addIntersection(new Intersection("C", "C", 10, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0, null));
        network.addRoad(new Road("BC", "B", "C", 10, 50, 100, 0, null));

        // Traversal order is C <- B <- A, reconstruction reverses
        Map<String, String> prev = new HashMap<>();
        prev.put("B", "A");
        prev.put("C", "B");
        Map<String, String> prevRoad = new HashMap<>();
        prevRoad.put("B", "AB");
        prevRoad.put("C", "BC");

        Route route = RouteBuilder.reconstructRoute(network, "A", "C", prev, prevRoad, RoutePreference.SHORTEST_DISTANCE);

        assertThat(route.getStepCount()).isEqualTo(2);
        List<RouteStep> steps = route.getSteps();
        assertThat(steps.get(0).getFromIntersectionId()).isEqualTo("A");
        assertThat(steps.get(0).getToIntersectionId()).isEqualTo("B");
        assertThat(steps.get(1).getFromIntersectionId()).isEqualTo("B");
        assertThat(steps.get(1).getToIntersectionId()).isEqualTo("C");
    }

    @Test
    @DisplayName("total distance equals sum of road distances")
    void totalDistanceSum() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addIntersection(new Intersection("C", "C", 10, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0, null));
        network.addRoad(new Road("BC", "B", "C", 15, 50, 100, 0, null));

        Map<String, String> prev = new HashMap<>();
        prev.put("B", "A");
        prev.put("C", "B");
        Map<String, String> prevRoad = new HashMap<>();
        prevRoad.put("B", "AB");
        prevRoad.put("C", "BC");

        Route route = RouteBuilder.reconstructRoute(network, "A", "C", prev, prevRoad, RoutePreference.SHORTEST_DISTANCE);

        assertThat(route.getTotalDistance()).isEqualTo(25.0);
    }

    @Test
    @DisplayName("total time equals sum of distance/speedLimit")
    void totalTimeSum() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addIntersection(new Intersection("C", "C", 10, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0, null));
        network.addRoad(new Road("BC", "B", "C", 20, 100, 100, 0, null));

        Map<String, String> prev = new HashMap<>();
        prev.put("B", "A");
        prev.put("C", "B");
        Map<String, String> prevRoad = new HashMap<>();
        prevRoad.put("B", "AB");
        prevRoad.put("C", "BC");

        Route route = RouteBuilder.reconstructRoute(network, "A", "C", prev, prevRoad, RoutePreference.SHORTEST_DISTANCE);

        // time = 10/50 + 20/100 = 0.2 + 0.2 = 0.4
        assertThat(route.getEstimatedTravelTime()).isCloseTo(0.4, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("total cost matches SHORTEST_DISTANCE weight (distance)")
    void totalCostDistance() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0, null));

        Map<String, String> prev = Map.of("B", "A");
        Map<String, String> prevRoad = Map.of("B", "AB");

        Route route = RouteBuilder.reconstructRoute(network, "A", "B", prev, prevRoad, RoutePreference.SHORTEST_DISTANCE);

        assertThat(route.getTotalCost()).isEqualTo(10.0);
    }

    @Test
    @DisplayName("total cost matches SHORTEST_TIME weight (time)")
    void totalCostTime() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0, null));

        Map<String, String> prev = Map.of("B", "A");
        Map<String, String> prevRoad = Map.of("B", "AB");

        Route route = RouteBuilder.reconstructRoute(network, "A", "B", prev, prevRoad, RoutePreference.SHORTEST_TIME);

        // cost = 10/50 = 0.2
        assertThat(route.getTotalCost()).isCloseTo(0.2, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("total cost matches CONGESTION_AWARE weight")
    void totalCostCongestionAware() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0,
                new RoadCondition(0.5, 0.3, RoadStatus.CONGESTED)));

        Map<String, String> prev = Map.of("B", "A");
        Map<String, String> prevRoad = Map.of("B", "AB");

        Route route = RouteBuilder.reconstructRoute(network, "A", "B", prev, prevRoad, RoutePreference.CONGESTION_AWARE);

        double expectedCost = network.getRoad("AB").calculateTravelCost();
        assertThat(route.getTotalCost()).isCloseTo(expectedCost, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("reconstruct 4-step route with correct steps")
    void fourStepRoute() {
        RoadNetwork network = TestGraph.buildLinearGraph();

        Map<String, String> prev = new HashMap<>();
        prev.put("B", "A");
        prev.put("C", "B");
        prev.put("D", "C");
        Map<String, String> prevRoad = new HashMap<>();
        prevRoad.put("B", "AB");
        prevRoad.put("C", "BC");
        prevRoad.put("D", "CD");

        Route route = RouteBuilder.reconstructRoute(network, "A", "D", prev, prevRoad, RoutePreference.SHORTEST_DISTANCE);

        assertThat(route.getStepCount()).isEqualTo(3);
        assertThat(route.getTotalDistance()).isEqualTo(15.0);
        assertThat(route.getSteps()).extracting(RouteStep::getRoadId)
                .containsExactly("AB", "BC", "CD");
    }
}
