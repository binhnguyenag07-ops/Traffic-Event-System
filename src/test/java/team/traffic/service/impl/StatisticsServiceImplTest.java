package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.simulation.TrafficSimulator;
import team.traffic.simulation.VehicleConfig;

class StatisticsServiceImplTest {

    @Test
    void aggregatesCompletedTripTimeDistanceAndDensity() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0,
                new RoadCondition(0.2, 0, RoadStatus.OPEN)));
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        RoutingServiceImpl routing = new RoutingServiceImpl(registry);
        TrafficSimulator simulator = new TrafficSimulator(network, routing);
        SimulationServiceImpl simulation = new SimulationServiceImpl(simulator);
        try {
            simulation.initialize(List.of(new VehicleConfig("car-1", "CAR", "A", "B")), List.of());
            simulator.setSimulationSecondsPerTick(3600);
            simulation.advanceTick();
            StatisticsServiceImpl service = new StatisticsServiceImpl(
                    simulation, new RoadNetworkServiceImpl(network), registry, routing);

            var statistics = service.getTrafficStatistics();

            assertThat(statistics.getArrivedVehicles()).isEqualTo(1);
            assertThat(statistics.getTotalTravelTimeSeconds()).isEqualTo(3600);
            assertThat(statistics.getAverageTravelTimeSeconds()).isEqualTo(3600);
            assertThat(statistics.getAverageRouteLengthKm()).isEqualTo(1);
            assertThat(statistics.getAverageVehicleDensity()).isZero();
        } finally {
            simulation.close();
        }
    }

    @Test
    void keepsCompletedTripMetricsAfterArrivedVehicleIsRemoved() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        RoutingServiceImpl routing = new RoutingServiceImpl(registry);
        TrafficSimulator simulator = new TrafficSimulator(network, routing);
        SimulationServiceImpl simulation = new SimulationServiceImpl(simulator);
        try {
            simulation.initialize(List.of(
                    new VehicleConfig("car-1", "CAR", "A", "B"),
                    new VehicleConfig("car-2", "CAR", "A", "B")), List.of());
            simulator.setSimulationSecondsPerTick(3600);
            simulation.advanceTick();
            simulation.removeVehicle("car-1");
            StatisticsServiceImpl service = new StatisticsServiceImpl(
                    simulation, new RoadNetworkServiceImpl(network), registry, routing);

            var statistics = service.getTrafficStatistics();

            assertThat(statistics.getTotalVehicles()).isEqualTo(1);
            assertThat(statistics.getArrivedVehicles()).isEqualTo(2);
            assertThat(statistics.getTotalTravelTimeSeconds()).isEqualTo(7200);
            assertThat(statistics.getAverageTravelTimeSeconds()).isEqualTo(3600);
            assertThat(statistics.getAverageRouteLengthKm()).isEqualTo(1);

            simulation.reset();
            assertThat(service.getTrafficStatistics().getArrivedVehicles()).isZero();
            assertThat(service.getTrafficStatistics().getTotalTravelTimeSeconds()).isZero();
        } finally {
            simulation.close();
        }
    }
}
