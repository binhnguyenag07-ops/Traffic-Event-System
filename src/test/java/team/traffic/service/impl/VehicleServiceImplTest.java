package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.Motorcycle;
import team.traffic.domain.vehicle.JourneyPhase;
import team.traffic.domain.demand.PlaceKind;
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
        Vehicle motorcycle = vehicleService.createVehicle(VehicleType.MOTORCYCLE, "A", "C", network);

        assertThat(first.getId()).startsWith("V-");
        assertThat(second.getId()).startsWith("V-");
        assertThat(first.getId()).isNotEqualTo(second.getId());
        assertThat(motorcycle).isInstanceOf(Motorcycle.class);

        assertThatThrownBy(() -> vehicleService.createVehicle(VehicleType.BUS, "UNKNOWN", "C", network))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Start intersection not found");
    }

    @Test
    void manualBuildingTripKeepsTheSelectedAccessesForDrivewayRendering() {
        RoadNetwork network = createLinearNetwork();
        network.addBuilding(new Building(
                "HOME", "Home", BuildingType.RESIDENTIAL,
                0, 0, null, null,
                List.of(new BuildingAccessPoint("HOME-A", "A", 10.0))));
        network.addBuilding(new Building(
                "WORK", "Work", BuildingType.OFFICE,
                2, 0, null, null,
                List.of(new BuildingAccessPoint("WORK-C", "C", 14.0))));
        VehicleServiceImpl service = new VehicleServiceImpl(null, createRoutingService());

        Vehicle vehicle = service.createVehicleBetweenBuildings(
                VehicleType.CAR, "HOME", "WORK", network);

        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.DEPARTING_ORIGIN);
        assertThat(vehicle.getOriginResolvedPlace().kind()).isEqualTo(PlaceKind.BUILDING);
        assertThat(vehicle.getOriginResolvedPlace().id()).isEqualTo("HOME");
        assertThat(vehicle.getOriginResolvedPlace().accessIntersectionId()).isEqualTo("A");
        assertThat(vehicle.getDestinationResolvedPlace().kind()).isEqualTo(PlaceKind.BUILDING);
        assertThat(vehicle.getDestinationResolvedPlace().id()).isEqualTo("WORK");
        assertThat(vehicle.getDestinationResolvedPlace().accessIntersectionId()).isEqualTo("C");
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
        network.addIntersection(new Intersection(id, id, x, 0));
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
