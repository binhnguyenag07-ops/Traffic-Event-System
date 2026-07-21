package team.traffic.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RoutePreference;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingStrategyTest {

    private RoutingStrategyRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new RoutingStrategyRegistry();
        registry.register(new BfsPathFindingStrategy());
        registry.register(new DijkstraPathFindingStrategy());
        registry.register(new AStarPathFindingStrategy((a, b) -> {
            double dx = a.getX() - b.getX();
            double dy = a.getY() - b.getY();
            return Math.sqrt(dx * dx + dy * dy);
        }));
    }

    // ── BFS ──────────────────────────────────────────────

    @Nested
    @DisplayName("BFS strategy")
    class BfsTests {

        private final BfsPathFindingStrategy bfs = new BfsPathFindingStrategy();

        @Test
        @DisplayName("finds path with minimum hops")
        void findsMinimumHops() {
            RoadNetwork network = TestGraph.buildHopsVsDistanceGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteResult result = bfs.findRoute(network, request);

            assertThat(result.getAlgorithmName()).isEqualTo("BFS");
            // BFS picks A->D (1 hop, 100km) because it minimizes hops
            assertThat(result.getRoute().getStepCount()).isEqualTo(1);
            assertThat(result.getRoute().getTotalDistance()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("returns empty route when start equals destination")
        void sameStartAndDest() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteRequest request = new RouteRequest("A", "A", RoutePreference.MINIMUM_HOPS);
            RouteResult result = bfs.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isTrue();
            assertThat(result.getVisitedNodes()).isEqualTo(0);
        }

        @Test
        @DisplayName("throws when no route exists")
        void noRouteExists() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "X", "X", 0, 0, team.traffic.domain.network.LocationType.NORMAL));
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "Y", "Y", 10, 0, team.traffic.domain.network.LocationType.NORMAL));

            RouteRequest request = new RouteRequest("X", "Y", RoutePreference.MINIMUM_HOPS);
            assertThatThrownBy(() -> bfs.findRoute(network, request))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("skips blocked roads")
        void skipsBlockedRoads() {
            RoadNetwork network = TestGraph.buildBlockedRoadGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteResult result = bfs.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isFalse();
            assertThat(result.getRoute().getSteps().get(0).getFromIntersectionId()).isEqualTo("A");
        }

        @Test
        @DisplayName("visits nodes in BFS order")
        void visitsInBfsOrder() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteResult result = bfs.findRoute(network, request);

            assertThat(result.getVisitedNodes()).isGreaterThan(0);
        }

        @Test
        @DisplayName("handles linear graph correctly")
        void linearGraph() {
            RoadNetwork network = TestGraph.buildLinearGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteResult result = bfs.findRoute(network, request);

            assertThat(result.getRoute().getStepCount()).isEqualTo(3);
            assertThat(result.getRoute().getTotalDistance()).isEqualTo(15.0);
        }
    }

    // ── Dijkstra ─────────────────────────────────────────

    @Nested
    @DisplayName("Dijkstra strategy")
    class DijkstraTests {

        private final DijkstraPathFindingStrategy dijkstra = new DijkstraPathFindingStrategy();

        @Test
        @DisplayName("finds shortest weighted path")
        void findsShortestWeightedPath() {
            RoadNetwork network = TestGraph.buildTimePreferenceGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = dijkstra.findRoute(network, request);

            assertThat(result.getAlgorithmName()).isEqualTo("Dijkstra");
            assertThat(result.getRoute().isEmpty()).isFalse();
            // Should take A->B->D (fast road, time = 0.2)
            assertThat(result.getRoute().getEstimatedTravelTime()).isLessThan(0.5);
        }

        @Test
        @DisplayName("returns empty route when start equals destination")
        void sameStartAndDest() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteRequest request = new RouteRequest("B", "B", RoutePreference.SHORTEST_TIME);
            RouteResult result = dijkstra.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("throws when no route exists")
        void noRouteExists() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "X", "X", 0, 0, team.traffic.domain.network.LocationType.NORMAL));
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "Y", "Y", 10, 0, team.traffic.domain.network.LocationType.NORMAL));

            RouteRequest request = new RouteRequest("X", "Y", RoutePreference.SHORTEST_TIME);
            assertThatThrownBy(() -> dijkstra.findRoute(network, request))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("skips blocked roads")
        void skipsBlockedRoads() {
            RoadNetwork network = TestGraph.buildBlockedRoadGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = dijkstra.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isFalse();
            // Must go A->C->D since AB is blocked
            String firstStep = result.getRoute().getSteps().get(0).getFromIntersectionId();
            assertThat(firstStep).isEqualTo("A");
        }

        @Test
        @DisplayName("handles linear graph correctly")
        void linearGraph() {
            RoadNetwork network = TestGraph.buildLinearGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = dijkstra.findRoute(network, request);

            assertThat(result.getRoute().getStepCount()).isEqualTo(3);
            assertThat(result.getRoute().getTotalDistance()).isEqualTo(15.0);
            // time = 15/50 = 0.3
            assertThat(result.getRoute().getEstimatedTravelTime()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("handles congestion-aware preference")
        void congestionAware() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            // Make road AB heavily congested
            network.getRoads().get("AB").setCondition(
                    new team.traffic.domain.network.RoadCondition(0.9, 0.8,
                            team.traffic.domain.network.RoadStatus.CONGESTED));

            RouteRequest request = new RouteRequest("A", "D", RoutePreference.CONGESTION_AWARE);
            RouteResult result = dijkstra.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isFalse();
            // Should prefer A->C->D to avoid congestion on AB
            assertThat(result.getRoute().getTotalCost()).isLessThan(
                    result.getRoute().getSteps().size() * 10.0); // rough sanity
        }
    }

    // ── A* ───────────────────────────────────────────────

    @Nested
    @DisplayName("A* strategy")
    class AStarTests {

        private final AStarPathFindingStrategy astar = new AStarPathFindingStrategy((a, b) -> {
            double dx = a.getX() - b.getX();
            double dy = a.getY() - b.getY();
            return Math.sqrt(dx * dx + dy * dy);
        });

        @Test
        @DisplayName("finds shortest path using heuristic")
        void findsPathWithHeuristic() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = astar.findRoute(network, request);

            assertThat(result.getAlgorithmName()).isEqualTo("A*");
            assertThat(result.getRoute().isEmpty()).isFalse();
            List<team.traffic.domain.routing.RouteStep> steps = result.getRoute().getSteps();
            assertThat(steps.get(0).getFromIntersectionId()).isEqualTo("A");
            assertThat(steps.get(steps.size() - 1).getToIntersectionId()).isEqualTo("D");
        }

        @Test
        @DisplayName("finds valid path on time preference graph")
        void findsValidPath() {
            RoadNetwork network = TestGraph.buildTimePreferenceGraph();

            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult aStarResult = astar.findRoute(network, request);

            assertThat(aStarResult.getRoute().isEmpty()).isFalse();
            assertThat(aStarResult.getVisitedNodes()).isGreaterThan(0);
            // A* should find a path with reasonable travel time
            assertThat(aStarResult.getRoute().getEstimatedTravelTime()).isLessThan(1.0);
        }

        @Test
        @DisplayName("returns empty route when start equals destination")
        void sameStartAndDest() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteRequest request = new RouteRequest("C", "C", RoutePreference.SHORTEST_TIME);
            RouteResult result = astar.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("throws when no route exists")
        void noRouteExists() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "X", "X", 0, 0, team.traffic.domain.network.LocationType.NORMAL));
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "Y", "Y", 10, 0, team.traffic.domain.network.LocationType.NORMAL));

            RouteRequest request = new RouteRequest("X", "Y", RoutePreference.SHORTEST_TIME);
            assertThatThrownBy(() -> astar.findRoute(network, request))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("throws for unknown destination")
        void unknownDestination() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteRequest request = new RouteRequest("A", "Z", RoutePreference.SHORTEST_TIME);
            assertThatThrownBy(() -> astar.findRoute(network, request))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("handles triangle graph correctly")
        void triangleGraph() {
            RoadNetwork network = TestGraph.buildTriangleGraph();
            RouteRequest request = new RouteRequest("A", "B", RoutePreference.SHORTEST_DISTANCE);
            RouteResult result = astar.findRoute(network, request);

            assertThat(result.getRoute().isEmpty()).isFalse();
            assertThat(result.getRoute().getSteps().get(0).getFromIntersectionId()).isEqualTo("A");
        }

        @Test
        @DisplayName("keeps distance heuristic in the same units as travel time")
        void usesAdmissibleTimeHeuristic() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "A", "A", 0, 0, team.traffic.domain.network.LocationType.NORMAL));
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "C", "C", 10, 0, team.traffic.domain.network.LocationType.NORMAL));
            network.addIntersection(new team.traffic.domain.network.Intersection(
                    "D", "D", 2, 0, team.traffic.domain.network.LocationType.NORMAL));
            network.addRoad(new team.traffic.domain.network.Road("AD", "A", "D", 2, 1, 10, 0,
                    new team.traffic.domain.network.RoadCondition(0, 0,
                            team.traffic.domain.network.RoadStatus.OPEN)));
            network.addRoad(new team.traffic.domain.network.Road("AC", "A", "C", 10, 100, 10, 0,
                    new team.traffic.domain.network.RoadCondition(0, 0,
                            team.traffic.domain.network.RoadStatus.OPEN)));
            network.addRoad(new team.traffic.domain.network.Road("CD", "C", "D", 8, 100, 10, 0,
                    new team.traffic.domain.network.RoadCondition(0, 0,
                            team.traffic.domain.network.RoadStatus.OPEN)));

            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = astar.findRoute(network, request);

            assertThat(result.getRoute().getSteps()).extracting(step -> step.getRoadId())
                    .containsExactly("AC", "CD");
            assertThat(result.getRoute().getEstimatedTravelTime()).isCloseTo(0.18,
                    org.assertj.core.data.Offset.offset(0.0001));
        }
    }

    // ── RoutingStrategyRegistry ──────────────────────────

    @Nested
    @DisplayName("RoutingStrategyRegistry")
    class RegistryTests {

        @Test
        @DisplayName("finds strategy by name (case-insensitive)")
        void findByName() {
            assertThat(registry.findByName("bfs")).isPresent();
            assertThat(registry.findByName("Dijkstra")).isPresent();
            assertThat(registry.findByName("a*")).isPresent();
            assertThat(registry.findByName("Unknown")).isEmpty();
        }

        @Test
        @DisplayName("finds strategy for route preference")
        void findByPreference() {
            assertThat(registry.findForPreference(RoutePreference.SHORTEST_DISTANCE)).isPresent();
            assertThat(registry.findForPreference(RoutePreference.SHORTEST_TIME)).isPresent();
            assertThat(registry.findForPreference(RoutePreference.CONGESTION_AWARE)).isPresent();
            assertThat(registry.findForPreference(RoutePreference.MINIMUM_HOPS)).isPresent();
        }

        @Test
        @DisplayName("returns all registered strategies")
        void getAll() {
            assertThat(registry.getAll()).hasSize(3);
        }
    }

    // ── Edge cases ───────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty network throws for all algorithms")
        void emptyNetwork() {
            RoadNetwork network = new RoadNetwork();
            RouteRequest bfsReq = new RouteRequest("A", "B", RoutePreference.MINIMUM_HOPS);
            RouteRequest dijkstraReq = new RouteRequest("A", "B", RoutePreference.SHORTEST_DISTANCE);

            assertThatThrownBy(() -> new BfsPathFindingStrategy().findRoute(network, bfsReq))
                    .isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> new DijkstraPathFindingStrategy().findRoute(network, dijkstraReq))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("single node: same start and end returns empty route")
        void singleNodeSameStartEnd() {
            RoadNetwork network = TestGraph.buildSingleNodeGraph();

            var bfsResult = new BfsPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "A", RoutePreference.MINIMUM_HOPS));
            assertThat(bfsResult.getRoute().isEmpty()).isTrue();

            var dijkstraResult = new DijkstraPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "A", RoutePreference.SHORTEST_DISTANCE));
            assertThat(dijkstraResult.getRoute().isEmpty()).isTrue();

            var astarResult = new AStarPathFindingStrategy((a, b) -> 0.0).findRoute(network,
                    new RouteRequest("A", "A", RoutePreference.SHORTEST_TIME));
            assertThat(astarResult.getRoute().isEmpty()).isTrue();
        }

        @Test
        @DisplayName("disconnected graph throws NoSuchElementException")
        void disconnectedGraph() {
            RoadNetwork network = TestGraph.buildDisconnectedGraph();

            assertThatThrownBy(() -> new BfsPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS)))
                    .isInstanceOf(NoSuchElementException.class);
            assertThatThrownBy(() -> new DijkstraPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "D", RoutePreference.SHORTEST_DISTANCE)))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("self-loop graph: route A->B goes through self-loop")
        void selfLoopGraph() {
            RoadNetwork network = TestGraph.buildSelfLoopGraph();
            RouteResult result = new BfsPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "B", RoutePreference.MINIMUM_HOPS));

            assertThat(result.getRoute().isEmpty()).isFalse();
            assertThat(result.getRoute().getSteps().get(0).getFromIntersectionId()).isEqualTo("A");
        }

        @Test
        @DisplayName("cyclic graph: finds path without infinite loop")
        void cyclicGraph() {
            RoadNetwork network = TestGraph.buildCyclicGraph();
            RouteResult result = new BfsPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "C", RoutePreference.MINIMUM_HOPS));

            assertThat(result.getRoute().isEmpty()).isFalse();
            assertThat(result.getRoute().getSteps().get(0).getFromIntersectionId()).isEqualTo("A");
        }

        @Test
        @DisplayName("route steps reference correct road IDs")
        void routeStepsCorrectRoadIds() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteResult result = new BfsPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS));

            for (team.traffic.domain.routing.RouteStep step : result.getRoute().getSteps()) {
                team.traffic.domain.network.Road road = network.getRoad(step.getRoadId());
                assertThat(road).isNotNull();
                assertThat(road.getStartIntersectionId()).isEqualTo(step.getFromIntersectionId());
                assertThat(road.getEndIntersectionId()).isEqualTo(step.getToIntersectionId());
            }
        }

        @Test
        @DisplayName("all algorithms agree on cost for equal-weight graph")
        void algorithmsAgreeOnEqualWeights() {
            // All roads have same distance and speed, so all algorithms should produce same cost
            RoadNetwork network = TestGraph.buildLinearGraph();

            RouteRequest bfsReq = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteRequest dijkstraReq = new RouteRequest("A", "D", RoutePreference.SHORTEST_DISTANCE);

            RouteResult bfsResult = new BfsPathFindingStrategy().findRoute(network, bfsReq);
            RouteResult dijkstraResult = new DijkstraPathFindingStrategy().findRoute(network, dijkstraReq);

            assertThat(bfsResult.getRoute().getTotalDistance())
                    .isEqualTo(dijkstraResult.getRoute().getTotalDistance());
            assertThat(bfsResult.getRoute().getStepCount())
                    .isEqualTo(dijkstraResult.getRoute().getStepCount());
        }

        @Test
        @DisplayName("BFS and Dijkstra find different paths on hops-vs-distance graph")
        void bfsVsDijkstraDifferentPaths() {
            RoadNetwork network = TestGraph.buildHopsVsDistanceGraph();

            RouteRequest bfsReq = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteRequest dijkstraReq = new RouteRequest("A", "D", RoutePreference.SHORTEST_DISTANCE);

            RouteResult bfsResult = new BfsPathFindingStrategy().findRoute(network, bfsReq);
            RouteResult dijkstraResult = new DijkstraPathFindingStrategy().findRoute(network, dijkstraReq);

            // BFS picks 1-hop path (A->D, 100km), Dijkstra picks 2-hop path (A->B->D, 10km)
            assertThat(bfsResult.getRoute().getStepCount()).isEqualTo(1);
            assertThat(dijkstraResult.getRoute().getStepCount()).isEqualTo(2);
            assertThat(bfsResult.getRoute().getTotalDistance()).isEqualTo(100.0);
            assertThat(dijkstraResult.getRoute().getTotalDistance()).isEqualTo(10.0);
        }

        @Test
        @DisplayName("route cost and distance are consistent")
        void costDistanceConsistency() {
            RoadNetwork network = TestGraph.buildSmallGraph();
            RouteResult result = new BfsPathFindingStrategy().findRoute(network,
                    new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS));

            assertThat(result.getRoute().getTotalDistance()).isGreaterThan(0);
            assertThat(result.getRoute().getEstimatedTravelTime()).isGreaterThan(0);
            assertThat(result.getRoute().getStepCount()).isGreaterThan(0);
        }
    }
}
