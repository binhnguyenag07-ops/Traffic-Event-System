package team.traffic.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.AStarPathFindingStrategy;
import team.traffic.routing.BfsPathFindingStrategy;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;

import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutingServiceImplTest {

    private RoutingStrategyRegistry registry;
    private RoutingServiceImpl service;
    private RoadNetwork network;

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
        service = new RoutingServiceImpl(registry);

        network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 10, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 0, 5, LocationType.NORMAL));
        network.addIntersection(new Intersection("D", "D", 10, 5, LocationType.NORMAL));

        network.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("BD", "B", "D", 10, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("AC", "A", "C", 5, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("CD", "C", "D", 5, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
    }

    @Test
    @DisplayName("constructor rejects null registry")
    void constructorRejectsNull() {
        assertThatThrownBy(() -> new RoutingServiceImpl(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("findRoute")
    class FindRouteTests {

        @Test
        @DisplayName("finds shortest distance route via Dijkstra")
        void findsShortestDistanceRoute() {
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_DISTANCE);
            RouteResult result = service.findRoute(network, request);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmName()).isEqualTo("Dijkstra");
            assertThat(result.getRoute().isEmpty()).isFalse();
            assertThat(result.getRoute().getSteps().get(0).getFromIntersectionId()).isEqualTo("A");
        }

        @Test
        @DisplayName("finds minimum hops route via BFS")
        void findsMinimumHopsRoute() {
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.MINIMUM_HOPS);
            RouteResult result = service.findRoute(network, request);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmName()).isEqualTo("BFS");
        }

        @Test
        @DisplayName("finds congestion-aware route via Dijkstra (first matching strategy)")
        void findsCongestionAwareRoute() {
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.CONGESTION_AWARE);
            RouteResult result = service.findRoute(network, request);

            assertThat(result).isNotNull();
            assertThat(result.getAlgorithmName()).isEqualTo("Dijkstra");
        }

        @Test
        @DisplayName("finds shortest time route via Dijkstra")
        void findsShortestTimeRoute() {
            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = service.findRoute(network, request);

            assertThat(result).isNotNull();
            assertThat(result.getRoute().isEmpty()).isFalse();
        }

        @Test
        @DisplayName("avoids blocked roads and uses the available alternative")
        void avoidsBlockedRoads() {
            network.getRoad("AB").getCondition().markBlocked();

            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_DISTANCE);
            RouteResult result = service.findRoute(network, request);

            assertThat(result.getRoute().getSteps())
                    .extracting(step -> step.getRoadId())
                    .containsExactly("AC", "CD");
        }

        @Test
        @DisplayName("avoids closed roads and uses the available alternative")
        void avoidsClosedRoads() {
            network.getRoad("AB").getCondition().setRoadStatus(RoadStatus.CLOSED);

            RouteRequest request = new RouteRequest("A", "D", RoutePreference.SHORTEST_TIME);
            RouteResult result = service.findRoute(network, request);

            assertThat(result.getRoute().getSteps())
                    .extracting(step -> step.getRoadId())
                    .containsExactly("AC", "CD");
        }

        @Test
        @DisplayName("keeps congested roads routable but penalizes them in congestion-aware routing")
        void penalizesCongestedRoads() {
            network.getRoad("AC").setCondition(new RoadCondition(1.0, 1.0, RoadStatus.CONGESTED));
            network.getRoad("CD").setCondition(new RoadCondition(1.0, 1.0, RoadStatus.CONGESTED));

            RouteRequest request = new RouteRequest("A", "D", RoutePreference.CONGESTION_AWARE);
            RouteResult result = service.findRoute(network, request);

            assertThat(result.getRoute().getSteps())
                    .extracting(step -> step.getRoadId())
                    .containsExactly("AB", "BD");
        }

        @Test
        @DisplayName("throws when no route exists")
        void throwsWhenNoRoute() {
            RoadNetwork disconnected = new RoadNetwork();
            disconnected.addIntersection(new Intersection("X", "X", 0, 0, LocationType.NORMAL));
            disconnected.addIntersection(new Intersection("Y", "Y", 10, 0, LocationType.NORMAL));

            RouteRequest request = new RouteRequest("X", "Y", RoutePreference.SHORTEST_DISTANCE);
            assertThatThrownBy(() -> service.findRoute(disconnected, request))
                    .isInstanceOf(NoSuchElementException.class);
        }

        @Test
        @DisplayName("rejects null network")
        void rejectsNullNetwork() {
            RouteRequest request = new RouteRequest("A", "B", RoutePreference.SHORTEST_DISTANCE);
            assertThatThrownBy(() -> service.findRoute(null, request))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("rejects null request")
        void rejectsNullRequest() {
            assertThatThrownBy(() -> service.findRoute(network, null))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
