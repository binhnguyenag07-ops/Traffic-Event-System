package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoutingService;
import team.traffic.service.impl.RoutingServiceImpl;

/**
 * The tick loop itself: recovering blocked vehicles, and republishing road
 * density from the space traffic physically takes up.
 */
class TickProcessorTest {

    @Test
    void aVehicleBlockedByAClosedRoadIsReroutedAndMovesOnTheAlternative() {
        RoadNetwork network = networkWithAlternativeRoute();
        TickProcessor processor = new TickProcessor(dijkstra());
        Vehicle vehicle = new Car("car-1", "A", "C");
        vehicle.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0), new RouteStep("B", "C", "BC", 1.0)),
                2.0, 2.0, 2.0));
        network.getRoad("AB").getCondition().setRoadStatus(RoadStatus.CLOSED);

        processor.process(0, network, List.of(vehicle), List.of());
        processor.process(1, network, List.of(vehicle), List.of());

        assertThat(vehicle.getOccupiedRoadId()).isEqualTo("AD");
        assertThat(vehicle.getStatus()).isIn(VehicleStatus.MOVING, VehicleStatus.WAITING);
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();
        assertThat(network.getRoad("AD").getCurrentVehicleCount()).isEqualTo(1);
    }

    @Test
    void roadDensityIsRepublishedFromTheSpaceTrafficActuallyTakesUp() {
        // A 100 m road with capacity 100: three cars are 3% of the headcount
        // but 21% of the tarmac.
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        TickProcessor processor = new TickProcessor(dijkstra());
        List<Vehicle> vehicles = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Car car = new Car("car-" + index, "A", "B");
            car.setCurrentRoute(new Route(List.of(new RouteStep("A", "B", "AB", 1.0)), 0.1, 1.0, 1.0));
            vehicles.add(car);
        }

        processor.process(0, network, vehicles, List.of());

        Road road = network.getRoad("AB");
        double headcountRatio = (double) road.getCurrentVehicleCount() / road.getCapacity();
        assertThat(road.getCondition().getVehicleDensity()).isGreaterThan(headcountRatio * 5);
    }

    @Test
    void aProcessorCannotBeBuiltWithoutADynamicsEngine() {
        assertThatThrownBy(() -> new TickProcessor(dijkstra(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dynamics engine");
    }

    private static RoutingService dijkstra() {
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        return new RoutingServiceImpl(registry);
    }

    /** A to C the direct way through B, or the long way round through D. */
    private static RoadNetwork networkWithAlternativeRoute() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 2, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("D", "D", 1, 1, LocationType.NORMAL));
        network.addRoad(openRoad("AB", "A", "B"));
        network.addRoad(openRoad("BC", "B", "C"));
        network.addRoad(openRoad("AD", "A", "D"));
        network.addRoad(openRoad("DC", "D", "C"));
        return network;
    }

    private static Road openRoad(String id, String from, String to) {
        return new Road(id, from, to, 1.0, 50.0, 10, 0, new RoadCondition(0, 0, RoadStatus.OPEN));
    }

}
