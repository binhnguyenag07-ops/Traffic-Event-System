package team.traffic.api.dto;

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
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;

class VehicleDtoTest {

    @Test
    void exposesContinuousPositionRouteAndTravelHistory() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1.0, 50.0, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        Vehicle vehicle = new Car("car-1", "A", "B");
        vehicle.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0)),
                1.0,
                1.0,
                1.0));
        vehicle.advance(36.0, network);

        VehicleDto dto = new VehicleDto(vehicle);

        assertThat(dto.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(dto.getCurrentRoadId()).isEqualTo("AB");
        assertThat(dto.getSegmentStartIntersectionId()).isEqualTo("A");
        assertThat(dto.getSegmentEndIntersectionId()).isEqualTo("B");
        assertThat(dto.getRoadProgress()).isEqualTo(0.5);
        assertThat(dto.getCurrentRoute()).singleElement().satisfies(step -> {
            assertThat(step.from).isEqualTo("A");
            assertThat(step.to).isEqualTo("B");
            assertThat(step.roadId).isEqualTo("AB");
        });
        assertThat(dto.getNextRouteStepIndex()).isZero();
        assertThat(dto.getTravelHistory()).containsExactly("A");
    }
}
