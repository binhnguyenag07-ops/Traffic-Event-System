package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;
import team.traffic.service.RoutingService;

class VehicleTest {

    @Test
    void requestsRouteAndMovesThroughEveryStep() {
        RoadNetwork network = createNetwork();
        Route route = routeFromAToC();
        RoutingService routingService = (ignoredNetwork, request) ->
                new RouteResult(route, "test", 1, 3);
        Vehicle vehicle = new Car("car-1", "A", "C");

        vehicle.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

        assertThat(vehicle.getCurrentRoute()).isSameAs(route);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.WAITING);

        vehicle.moveOneStep(network);
        assertThat(vehicle.getCurrentIntersectionId()).isEqualTo("B");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.MOVING);
        assertThat(vehicle.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isEqualTo(1);

        vehicle.moveOneStep(network);
        assertThat(vehicle.getCurrentIntersectionId()).isEqualTo("C");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ARRIVED);
        assertThat(vehicle.getTravelHistory()).containsExactly("A", "B", "C");
        assertThat(vehicle.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();
        assertThat(network.getRoad("BC").getCurrentVehicleCount()).isZero();
    }

    @Test
    void becomesBlockedWhenRoutingCannotFindAPath() {
        RoadNetwork network = createNetwork();
        RoutingService routingService = (ignoredNetwork, request) -> {
            throw new NoSuchElementException("No route");
        };
        Vehicle vehicle = new Car("car-1", "A", "C");

        vehicle.requestRoute(network, routingService);

        assertThat(vehicle.getCurrentRoute()).isNull();
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BLOCKED);
    }

    @Test
    void doesNotMoveWhenTheNextRoadIsClosed() {
        RoadNetwork network = createNetwork();
        Route route = routeFromAToC();
        Vehicle vehicle = new Car("car-1", "A", "C");
        vehicle.setCurrentRoute(route);
        network.getRoad("AB").getCondition().setRoadStatus(RoadStatus.CLOSED);

        vehicle.moveOneStep(network);

        assertThat(vehicle.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BLOCKED);
    }

    @Test
    void recomputesRouteFromTheCurrentIntersection() {
        RoadNetwork network = createNetwork();
        Vehicle vehicle = new Car("car-1", "A", "C");
        vehicle.moveTo("B");
        Route route = new Route(
                List.of(new RouteStep("B", "C", "BC", 1.0)),
                1.0,
                1.0,
                1.0);
        RoutingService routingService = (ignoredNetwork, request) -> {
            assertThat(request.getStartIntersectionId()).isEqualTo("B");
            assertThat(request.getDestinationIntersectionId()).isEqualTo("C");
            return new RouteResult(route, "test", 1, 2);
        };

        vehicle.recomputeRoute(network, routingService, RoutePreference.CONGESTION_AWARE);

        assertThat(vehicle.getCurrentRoute()).isSameAs(route);
        assertThat(vehicle.getRoutePreference()).isEqualTo(RoutePreference.CONGESTION_AWARE);
        assertThat(vehicle.getNextRouteStepIndex()).isZero();
    }

    private RoadNetwork createNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 2, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BC", "B", "C"));
        return network;
    }

    private Road road(String id, String from, String to) {
        return new Road(
                id,
                from,
                to,
                1.0,
                50.0,
                10,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }

    private Route routeFromAToC() {
        return new Route(
                List.of(
                        new RouteStep("A", "B", "AB", 1.0),
                        new RouteStep("B", "C", "BC", 1.0)),
                2.0,
                2.0,
                2.0);
    }
}
