package team.traffic.api.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;

class SimulationSnapshotDtoTest {

    @Test
    void aSnapshotWithANetworkCarriesEachVehiclesMapPositionAndHeading() {
        RoadNetwork network = geographicNetwork();
        Vehicle vehicle = new Car("car-1", "A", "B");

        SimulationSnapshotDto dto = new SimulationSnapshotDto(snapshotOf(vehicle), network);

        VehicleDto vehicleDto = dto.getVehicles().get(0);
        assertThat(vehicleDto.getLatitude()).isCloseTo(10.770, within(1e-6));
        assertThat(vehicleDto.getLongitude()).isCloseTo(106.700, within(1e-6));
        assertThat(vehicleDto.getHeadingDegrees()).isCloseTo(0.0, within(1.0));
        assertThat(vehicleDto.isInJunction()).isTrue();
    }

    @Test
    void aSnapshotWithoutANetworkLeavesThePositionUnresolved() {
        SimulationSnapshotDto dto = new SimulationSnapshotDto(snapshotOf(new Car("car-1", "A", "B")));

        VehicleDto vehicleDto = dto.getVehicles().get(0);
        assertThat(vehicleDto.getLatitude()).isNull();
        assertThat(vehicleDto.getHeadingDegrees()).isNull();
    }

    @Test
    void aVehicleCarriesBothTheSpeedItWantsAndTheSpeedItIsDoing() {
        Vehicle vehicle = new Car("car-1", "A", "B");
        vehicle.setInstantaneousSpeedMps(5.0);

        VehicleDto dto = new VehicleDto(vehicle);

        assertThat(dto.getCruisingSpeedKph()).isEqualTo(50.0);
        assertThat(dto.getInstantaneousSpeed()).isCloseTo(18.0, within(1e-9));
    }

    private static SimulationSnapshot snapshotOf(Vehicle vehicle) {
        return new SimulationSnapshot(0, SimulationStatus.READY, List.of(vehicle), List.of());
    }

    private static RoadNetwork geographicNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection(
                "A", "A", 106.700, 10.770, LocationType.NORMAL, 10.770, 106.700));
        network.addIntersection(new Intersection(
                "B", "B", 106.700, 10.780, LocationType.NORMAL, 10.780, 106.700));
        network.addRoad(new Road("AB", "A", "B", 1.1, 50, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.770, 106.700), new GeoPoint(10.780, 106.700))));
        return network;
    }
}
