package team.traffic.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.vehicle.Car;
import team.traffic.service.RoutingService;
import team.traffic.service.impl.RoutingServiceImpl;

class BuildingRoutePlannerTest {
    @Test
    void choosesCheapestEntranceAndChangesItAfterRoadClosure() {
        RoadNetwork network = network();
        RoutingService routing = routing();
        BuildingRoutePlanner planner = new BuildingRoutePlanner(routing);

        var first = planner.findRouteToBuilding(
                network, "START", "DEST", RoutePreference.CONGESTION_AWARE);

        assertThat(first.destinationAccess().intersectionId()).isEqualTo("EAST_ACCESS");
        assertThat(first.routeResult().getRoute().getSteps())
                .extracting(step -> step.getRoadId())
                .containsExactly("START-EAST");

        network.updateRoadCondition("START-EAST", new RoadCondition(1, 0, RoadStatus.BLOCKED));
        var rerouted = planner.findRouteToBuilding(
                network, "START", "DEST", RoutePreference.CONGESTION_AWARE);

        assertThat(rerouted.destinationAccess().intersectionId()).isEqualTo("WEST_ACCESS");
    }

    @Test
    void buildingVehicleRerouteCanSelectAnotherEntrance() {
        RoadNetwork network = network();
        RoutingService routing = routing();
        Car vehicle = new Car("V1", "START", "WEST_ACCESS");
        vehicle.assignBuildingTrip("ORIGIN", "DEST");

        vehicle.requestRoute(network, routing);
        assertThat(vehicle.getDestinationIntersectionId()).isEqualTo("EAST_ACCESS");

        network.updateRoadCondition("START-EAST", new RoadCondition(1, 0, RoadStatus.BLOCKED));
        vehicle.recomputeRoute(network, routing);
        assertThat(vehicle.getDestinationIntersectionId()).isEqualTo("WEST_ACCESS");
    }

    private RoutingService routing() {
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        return new RoutingServiceImpl(registry);
    }

    private RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        addNode(network, "START", 0);
        addNode(network, "WEST_ACCESS", 1);
        addNode(network, "EAST_ACCESS", 2);
        network.addRoad(road("START-WEST", "START", "WEST_ACCESS", 5.0));
        network.addRoad(road("START-EAST", "START", "EAST_ACCESS", 1.0));
        network.addBuilding(new Building(
                "ORIGIN", "Origin", BuildingType.RESIDENTIAL, 0, 0, null, null,
                List.of(new BuildingAccessPoint("ORIGIN-ENTRY", "START", 5))));
        network.addBuilding(new Building(
                "DEST", "Destination", BuildingType.COMMERCIAL, 2, 0, null, null,
                List.of(
                        new BuildingAccessPoint("DEST-WEST", "WEST_ACCESS", 5),
                        new BuildingAccessPoint("DEST-EAST", "EAST_ACCESS", 5))));
        return network;
    }

    private void addNode(RoadNetwork network, String id, double x) {
        network.addIntersection(new Intersection(id, id, x, 0));
    }

    private Road road(String id, String from, String to, double distance) {
        return new Road(id, from, to, distance, 50, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
