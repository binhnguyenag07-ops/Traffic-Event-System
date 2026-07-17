package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

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

class VehicleProgressTest {

    @Test
    void advancesAcrossARoadUsingSpeedAndSimulationTime() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1.0, 50.0, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        Car car = new Car("car-1", "A", "B");
        car.setCurrentRoute(new Route(List.of(new RouteStep("A", "B", "AB", 1.0)), 1.0, 0.02, 1.0));

        car.advance(network, 36.0);

        assertThat(car.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(car.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(car.getRoadProgress()).isCloseTo(0.454545, within(0.0001));
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isEqualTo(1);

        car.advance(network, 36.0);

        assertThat(car.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(car.getRoadProgress()).isCloseTo(0.90909, within(0.0001));

        car.advance(network, 36.0);

        assertThat(car.getCurrentIntersectionId()).isEqualTo("B");
        assertThat(car.getStatus()).isEqualTo(VehicleStatus.ARRIVED);
        assertThat(car.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();
    }

    private static org.assertj.core.data.Offset<Double> within(double value) {
        return org.assertj.core.data.Offset.offset(value);
    }
}
