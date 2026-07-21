package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoutingService;

class VehicleServiceImplTest {

    private final VehicleServiceImpl vehicleService = new VehicleServiceImpl();

    @Test
    void createsVehiclesWithUniqueGeneratedIdsAndValidatesIntersections() {
        RoadNetwork network = createNetworkWithAlternativeRoute();

        Vehicle first = vehicleService.createVehicle(VehicleType.CAR, "A", "C", network);
        Vehicle second = vehicleService.createVehicle(VehicleType.CAR, "A", "C", network);

        assertThat(first.getId()).startsWith("V-");
        assertThat(second.getId()).startsWith("V-");
        assertThat(first.getId()).isNotEqualTo(second.getId());

        assertThatThrownBy(() -> vehicleService.createVehicle(VehicleType.BUS, "UNKNOWN", "C", network))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start intersection not found");
    }

    private RoutingService createRoutingService() {
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        return new RoutingServiceImpl(registry);
    }

    private RoadNetwork createLinearNetwork() {
        RoadNetwork network = new RoadNetwork();
        addIntersection(network, "A", 0);
        addIntersection(network, "B", 1);
        addIntersection(network, "C", 2);
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BC", "B", "C"));
        return network;
    }

    private RoadNetwork createNetworkWithAlternativeRoute() {
        RoadNetwork network = createLinearNetwork();
        addIntersection(network, "D", 1);
        network.addRoad(road("AD", "A", "D"));
        network.addRoad(road("DC", "D", "C"));
        return network;
    }

    private void addIntersection(RoadNetwork network, String id, double x) {
        network.addIntersection(new Intersection(id, id, x, 0, LocationType.NORMAL));
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
}
