package team.traffic.domain.vehicle.pose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/**
 * A corner: the vehicle runs north along AB, then turns east along BC.
 */
class VehiclePoseResolverTest {

    private final VehiclePoseResolver resolver = new VehiclePoseResolver();
    private RoadNetwork network;
    private Car car;

    @BeforeEach
    void setUp() {
        network = new RoadNetwork();
        network.addIntersection(junction("A", 10.770, 106.700));
        network.addIntersection(junction("B", 10.780, 106.700));
        network.addIntersection(junction("C", 10.780, 106.710));
        network.addRoad(road("AB", "A", "B",
                new GeoPoint(10.770, 106.700), new GeoPoint(10.780, 106.700)));
        network.addRoad(road("BC", "B", "C",
                new GeoPoint(10.780, 106.700), new GeoPoint(10.780, 106.710)));

        car = new Car("car-1", "A", "C");
        car.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0), new RouteStep("B", "C", "BC", 1.0)),
                2.2, 1.0, 1.0));
    }

    @Test
    void aVehicleOnARoadSitsOnThatRoadAndPointsAlongIt() {
        drive(1);

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.roadId()).isEqualTo("AB");
        assertThat(pose.inJunction()).isFalse();
        assertThat(pose.headingDegrees()).isCloseTo(0.0, within(1.0));
        assertThat(pose.latitude()).isBetween(10.770, 10.780);
        assertThat(pose.longitude()).isCloseTo(106.700, within(1e-6));
    }

    @Test
    void aVehicleCarriesItsRealSizeSoItCanBeDrawnToScale() {
        drive(1);

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.lengthMetres()).isEqualTo(car.getLength());
        assertThat(pose.widthMetres()).isEqualTo(car.getWidth());
    }

    @Test
    void aVehicleThatHasNotStartedFacesTheRoadItIsAboutToTake() {
        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.inJunction()).isTrue();
        assertThat(pose.roadId()).isNull();
        assertThat(pose.headingDegrees()).isCloseTo(0.0, within(1.0));
        assertThat(pose.latitude()).isCloseTo(10.770, within(1e-9));
    }

    @Test
    void aVehicleWaitingAtAJunctionKeepsFacingTheWayItArrived() {
        driveUntilWaitingAt("B");

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.inJunction()).isTrue();
        // It came up AB heading north; it must not snap round to any other bearing.
        assertThat(pose.headingDegrees()).isCloseTo(0.0, within(1.0));
        assertThat(pose.latitude()).isCloseTo(10.780, within(1e-9));
        assertThat(pose.longitude()).isCloseTo(106.700, within(1e-9));
    }

    @Test
    void everyVehicleHasAPoseAtEveryMomentOfItsTrip() {
        for (int tick = 0; tick < 40; tick++) {
            assertThat(resolver.resolve(car, network))
                    .as("pose at tick %d", tick)
                    .isPresent();
            drive(1);
        }
    }

    @Test
    void theHeadingTurnsOnceTheVehicleIsOnTheSecondRoad() {
        driveUntilOn("BC");

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.roadId()).isEqualTo("BC");
        assertThat(pose.headingDegrees()).isCloseTo(90.0, within(1.0));
    }

    @Test
    void aScenarioWithoutGeographicCoordinatesHasNoPose() {
        RoadNetwork planar = new RoadNetwork();
        planar.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        planar.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        planar.addRoad(new Road("AB", "A", "B", 1.0, 50, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        assertThat(resolver.resolve(new Car("car-2", "A", "B"), planar)).isEmpty();
    }

    @Test
    void anUnknownVehicleOrNetworkIsHandledWithoutFailing() {
        assertThat(resolver.resolve(null, network)).isEmpty();
        assertThat(resolver.resolve(car, null)).isEmpty();
    }

    private void drive(int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            TrafficState state = TrafficState.capture(tick, network, List.of(car));
            car.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
            state.resolveJunctions();
            car.advance(network, state, 10.0);
        }
    }

    private void driveUntilWaitingAt(String junctionId) {
        for (int tick = 0; tick < 60; tick++) {
            if (car.getOccupiedRoadId() == null && junctionId.equals(car.getCurrentIntersectionId())
                    && car.getNextRouteStepIndex() > 0) {
                return;
            }
            drive(1);
        }
        throw new AssertionError("Vehicle never waited at " + junctionId);
    }

    private void driveUntilOn(String roadId) {
        for (int tick = 0; tick < 60; tick++) {
            if (roadId.equals(car.getOccupiedRoadId())) {
                return;
            }
            drive(1);
        }
        throw new AssertionError("Vehicle never reached " + roadId);
    }

    private static Intersection junction(String id, double latitude, double longitude) {
        return new Intersection(id, id, longitude, latitude, LocationType.NORMAL, latitude, longitude);
    }

    private static Road road(String id, String from, String to, GeoPoint start, GeoPoint end) {
        return new Road(id, from, to, 1.1, 50, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN), List.of(start, end));
    }
}
