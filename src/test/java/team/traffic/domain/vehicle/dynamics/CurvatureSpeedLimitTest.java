package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.simulation.VehicleDynamicsEngine;

class CurvatureSpeedLimitTest {
    private static final DriverProfile DRIVER = DriverProfile.forType(
            team.traffic.domain.vehicle.VehicleType.CAR);

    @Test
    void aSharpPolylineCapsSpeedBelowAnOtherwiseEquivalentStraightRoad() {
        Scenario sharp = scenario(List.of(
                new GeoPoint(10.77000, 106.70000),
                new GeoPoint(10.77009, 106.70000),
                new GeoPoint(10.77009, 106.70009)));
        Scenario straight = scenario(List.of(
                new GeoPoint(10.77000, 106.70000),
                new GeoPoint(10.77009, 106.70000),
                new GeoPoint(10.77018, 106.70000)));

        VehicleDynamicsEngine engine = new VehicleDynamicsEngine();
        engine.step(0, 0, sharp.network(), List.of(sharp.vehicle()), List.of(), 5.0);
        engine.step(0, 0, straight.network(), List.of(straight.vehicle()), List.of(), 5.0);

        assertThat(sharp.vehicle().getInstantaneousSpeedMps())
                .isLessThan(straight.vehicle().getInstantaneousSpeedMps());
        double radius = team.traffic.domain.vehicle.pose.PolylineGeometry
                .curvatureRadiusAt(sharp.network().getRoad("R").getGeometry(), 0.5);
        assertThat(sharp.vehicle().getInstantaneousSpeedMps())
                .isLessThanOrEqualTo(Math.sqrt(DRIVER.lateralAccelerationMps2() * radius) + 1e-9);
    }

    private static Scenario scenario(List<GeoPoint> geometry) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addRoad(new Road("R", "Road", "P", "A", "B", 0.1, 100, 100, 0,
                1, 3.0, new RoadCondition(0, 0, RoadStatus.OPEN), geometry, RoadClass.RESIDENTIAL));
        Car car = new Car("same-driver", "A", "B");
        car.setDriverProfile(DRIVER);
        car.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "R", 1.0)), 0.1, 1.0, 1.0));
        return new Scenario(network, car);
    }

    private record Scenario(RoadNetwork network, Vehicle vehicle) {}
}
