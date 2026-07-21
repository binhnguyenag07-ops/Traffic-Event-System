package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.TrafficLightDelayEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoutingServiceImpl;

class TrafficLightWaitingTest {

    @Test
    void vehicleWaitsConfiguredTicksBeforeEnteringRoad() {
        Fixture fixture = fixture(3);

        fixture.processor.process(0, fixture.network, List.of(fixture.car), List.of(fixture.event), 3600);
        assertThat(fixture.car.getStatus()).isEqualTo(VehicleStatus.WAITING);
        assertThat(fixture.car.getWaitTicksRemaining()).isEqualTo(2);
        fixture.processor.process(1, fixture.network, List.of(fixture.car), List.of(fixture.event), 3600);
        fixture.processor.process(2, fixture.network, List.of(fixture.car), List.of(fixture.event), 3600);

        assertThat(fixture.car.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(fixture.car.getWaitTicksRemaining()).isZero();

        fixture.processor.process(3, fixture.network, List.of(fixture.car), List.of(fixture.event), 3600);
        assertThat(fixture.car.getCurrentIntersectionId()).isEqualTo("B");
        assertThat(fixture.car.getStatus()).isEqualTo(VehicleStatus.ARRIVED);
    }

    @Test
    void resolvingLightEventReleasesWaitingVehicle() {
        Fixture fixture = fixture(5);
        fixture.processor.process(0, fixture.network, List.of(fixture.car), List.of(fixture.event), 3600);
        fixture.event.resolve(fixture.network);

        fixture.processor.process(1, fixture.network, List.of(fixture.car), List.of(fixture.event), 3600);

        assertThat(fixture.car.getCurrentIntersectionId()).isEqualTo("B");
        assertThat(fixture.car.getWaitTicksRemaining()).isZero();
    }

    private Fixture fixture(long delayTicks) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        Car car = new Car("car-1", "A", "B");
        car.setCurrentRoute(new Route(List.of(new RouteStep("A", "B", "AB", 0.02)), 1, 0.02, 0.02));
        TrafficLightDelayEvent event = new TrafficLightDelayEvent("light-1", "AB", 0, 10, delayTicks);
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        return new Fixture(network, car, event, new TickProcessor(new RoutingServiceImpl(registry)));
    }

    private record Fixture(RoadNetwork network, Car car, TrafficLightDelayEvent event, TickProcessor processor) {}
}
