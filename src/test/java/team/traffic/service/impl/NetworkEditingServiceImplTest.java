package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Car;
import team.traffic.exception.NetworkEditConflictException;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.NetworkEditingService.RoadCreationRequest;
import team.traffic.service.RoadGeometryProvider.RoadGeometryResult;
import team.traffic.simulation.TrafficSimulator;

class NetworkEditingServiceImplTest {
    private RoadNetwork network;
    private TrafficSimulator simulator;
    private SimulationServiceImpl simulationService;
    private NetworkEditingServiceImpl editingService;

    @BeforeEach
    void setUp() {
        network = createNetwork();
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        RoutingServiceImpl routingService = new RoutingServiceImpl(registry);
        simulator = new TrafficSimulator(network, routingService);
        simulationService = new SimulationServiceImpl(simulator);
        editingService = new NetworkEditingServiceImpl(simulator, simulationService, routingService,
                (start, end) -> new RoadGeometryResult(List.of(
                        new GeoPoint(start.getLatitude(), start.getLongitude()),
                        new GeoPoint(end.getLatitude(), end.getLongitude())), 1, "test"));
    }

    @AfterEach
    void tearDown() {
        simulationService.close();
    }

    @Test
    void addsTwoWayRoadsAndFindsThemByIntersection() {
        var outcome = editingService.addRoads(new RoadCreationRequest(
                "BC", "CB", "B", "C", 40, 25, true, RoadClass.RESIDENTIAL,
                2, 3.4, "Connector", "BC-CB"));

        assertThat(outcome.roads()).extracting(Road::getId).containsExactly("BC", "CB");
        assertThat(outcome.roads()).extracting(Road::getRoadClass)
                .containsOnly(RoadClass.RESIDENTIAL);
        assertThat(outcome.roads()).extracting(Road::getName).containsOnly("Connector");
        assertThat(outcome.roads()).extracting(Road::getPhysicalRoadId).containsOnly("BC-CB");
        assertThat(outcome.roads()).extracting(Road::getLaneCount).containsOnly(2);
        assertThat(outcome.roads()).extracting(Road::getLaneWidthMetres).containsOnly(3.4);
        assertThat(outcome.geometrySources()).containsExactly("test", "test");
        assertThat(editingService.findConnectedRoads("B").outgoing())
                .extracting(Road::getId).contains("BC");
        assertThat(editingService.findConnectedRoads("B").incoming())
                .extracting(Road::getId).contains("CB");
    }

    @Test
    void reroutesWaitingVehiclesWhenRoadBecomesBlocked() {
        Car car = new Car("car-1", "A", "D");
        simulator.addVehicle(car);
        assertThat(car.getCurrentRoute().getSteps())
                .extracting(step -> step.getRoadId()).containsExactly("AB", "BD");

        editingService.updateRoadCondition("BD", RoadStatus.BLOCKED, 1);

        assertThat(car.getCurrentRoute().getSteps())
                .extracting(step -> step.getRoadId()).containsExactly("AC", "CD");
    }

    @Test
    void rejectsEditsWhileSimulationIsRunning() {
        simulator.start();

        assertThatThrownBy(() -> editingService.removeRoad("AB"))
                .isInstanceOf(NetworkEditConflictException.class)
                .hasMessageContaining("Pause");
    }

    @Test
    void rejectsDeletingAnOccupiedRoad() {
        simulator.addVehicle(new Car("car-1", "A", "D"));
        simulator.advanceTick();

        assertThatThrownBy(() -> editingService.removeRoad("AB"))
                .isInstanceOf(NetworkEditConflictException.class)
                .hasMessageContaining("occupied");
        assertThat(network.containsRoad("AB")).isTrue();
    }

    @Test
    void exportsTheEditedNetworkAsJson() throws Exception {
        editingService.addIntersection("E", "Edited junction", 10.775, 106.702);

        assertThat(editingService.exportJson())
                .contains("\"id\" : \"E\"")
                .contains("\"name\" : \"Edited junction\"");
    }

    private RoadNetwork createNetwork() {
        RoadNetwork result = new RoadNetwork();
        result.addIntersection(intersection("A", 10.770, 106.700));
        result.addIntersection(intersection("B", 10.771, 106.701));
        result.addIntersection(intersection("C", 10.772, 106.702));
        result.addIntersection(intersection("D", 10.773, 106.703));
        result.addRoad(road("AB", "A", "B", 1));
        result.addRoad(road("BD", "B", "D", 1));
        result.addRoad(road("AC", "A", "C", 2));
        result.addRoad(road("CD", "C", "D", 2));
        return result;
    }

    private Intersection intersection(String id, double latitude, double longitude) {
        return new Intersection(id, id, 0, 0, latitude, longitude);
    }

    private Road road(String id, String start, String end, double distance) {
        return new Road(id, start, end, distance, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
