package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class RoadNetworkServiceImplTest {
    private RoadNetwork network;
    private RoadNetworkServiceImpl service;

    @BeforeEach
    void setUp() {
        network = new RoadNetwork();
        network.addIntersection(new Intersection("I1", "First", 0, 0));
        network.addIntersection(new Intersection("I2", "Second", 1, 0));
        network.addRoad(new Road(
                "R1",
                "I1",
                "I2",
                1,
                50,
                10,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        service = new RoadNetworkServiceImpl(network);
    }

    @Test
    void delegatesVehicleDensityUpdatesThroughServiceContract() {
        Road road = service.recordVehicleEnteringRoad("R1");
        service.recordVehicleEnteringRoad("R1");

        assertThat(road.getCurrentVehicleCount()).isEqualTo(2);
        assertThat(service.updateVehicleDensity("R1")).isEqualTo(0.2);
        assertThat(service.getAverageVehicleDensity()).isEqualTo(0.2);
        assertThat(service.findRoadsAtOrAboveDensity(0.2))
                .extracting(Road::getId)
                .containsExactly("R1");

        service.recordVehicleLeavingRoad("R1");

        assertThat(road.getCurrentVehicleCount()).isEqualTo(1);
        assertThat(road.getCondition().getVehicleDensity()).isEqualTo(0.1);
    }
}
