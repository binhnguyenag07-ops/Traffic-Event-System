package team.traffic.domain.vehicle.pose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.JourneyPhase;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.history.SimulationFrame;

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
    void aLegacyIntersectionVehicleThatHasNotStartedIsNotDrawnAtAll() {
        // Unlike a resolved building or boundary source, a bare graph
        // intersection has no honest off-network departure pose. It also holds
        // no road, so putting it on tarmac would make the picture disagree with
        // occupancy and allow traffic to drive through a visible body.
        assertThat(resolver.resolve(car, network)).isEmpty();
    }

    @Test
    void aVehicleWaitingAtAJunctionKeepsFacingTheWayItArrived() {
        driveUntilWaitingAt("B");

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.inJunction()).isTrue();
        // It came up AB heading north; it must not snap round to any other bearing.
        assertThat(pose.headingDegrees()).isCloseTo(0.0, within(1.0));
        assertThat(pose.longitude()).isCloseTo(106.700, within(1e-9));
    }

    @Test
    void aVehicleWaitingToCrossHoldsAtTheStopLineRatherThanInTheJunction() {
        driveUntilWaitingAt("B");

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        // Junction B is at latitude 10.780. Waiting on that exact coordinate put
        // the vehicle in the middle of the crossing — and at a four-way, on the
        // same point as the leading vehicle of all three other approaches. It
        // now holds clear of the crossing, by as much of it as there is.
        double metresShortOfJunction = (10.780 - pose.latitude()) * 111_320.0;
        double junctionRadius = new RoadExtent(network, new RoadDataRoadLayout())
                .junctionRadiusMetres("B");
        assertThat(junctionRadius).isPositive();
        assertThat(metresShortOfJunction)
                .as("the pose is the centre, half a vehicle behind its front bumper")
                .isCloseTo(junctionRadius + car.getLength() / 2.0, within(0.01));
        double frontBumperShortOfJunction =
                metresShortOfJunction - car.getLength() / 2.0;
        assertThat(frontBumperShortOfJunction)
                .as("the authoritative front bumper stops exactly at the painted line")
                .isCloseTo(junctionRadius, within(0.01));
    }

    @Test
    void aBuildingDemandVehicleStartsAtItsBuildingBeforeEnteringTheRoad() {
        Building home = new Building(
                "HOME",
                "Home",
                BuildingType.RESIDENTIAL,
                0,
                0,
                10.7699,
                106.6999,
                List.of(new BuildingAccessPoint("HOME-A", "A", 16.0)));
        network.addBuilding(home);
        car.assignBuildingTrip("HOME", "OFFICE");

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(car.getJourneyPhase()).isEqualTo(JourneyPhase.DEPARTING_ORIGIN);
        assertThat(pose.roadId()).isNull();
        assertThat(pose.inJunction()).isFalse();
        assertThat(pose.latitude()).isEqualTo(home.getLatitude());
        assertThat(pose.longitude()).isEqualTo(home.getLongitude());
    }

    @Test
    void aQueueAtAStopLineFormsBehindTheVehicleThatArrivedFirst() {
        Car second = new Car("car-2", "A", "C");
        second.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0), new RouteStep("B", "C", "BC", 1.0)),
                2.2, 1.0, 1.0));
        driveUntilWaitingAt("B");
        for (int tick = 0; tick < 60 && second.getOccupiedRoadId() != null
                || second.getNextRouteStepIndex() == 0; tick++) {
            TrafficState state = TrafficState.capture(tick, network, List.of(car, second));
            second.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
            state.resolveJunctions();
            second.advance(network, state, 10.0);
            if (second.getOccupiedRoadId() == null && second.getNextRouteStepIndex() > 0) break;
        }

        var poses = resolver.resolveAll(List.of(second, car), network);

        // The first to arrive stands nearer the junction, whatever order the
        // list happens to be in and whatever the ids sort to.
        assertThat(poses.get("car-1").latitude()).isGreaterThan(poses.get("car-2").latitude());
    }

    @Test
    void aVehicleHasAPoseAtEveryMomentOnceItHasMovedOff() {
        // Once away it must never blink out — including while it holds no road,
        // waiting to cross a junction. A gap there would read on screen as the
        // vehicle teleporting across the crossing.
        drive(1);
        for (int tick = 1; tick < 40; tick++) {
            assertThat(resolver.resolve(car, network))
                    .as("pose at tick %d", tick)
                    .isPresent();
            drive(1);
        }
    }

    @Test
    void aLegacyIntersectionArrivalRemainsAtItsFinalApproach() {
        for (int tick = 0; tick < 120 && !car.hasReachedDestination(); tick++) {
            drive(1);
        }
        assertThat(car.hasReachedDestination()).isTrue();

        VehiclePose pose = resolver.resolveAll(List.of(car), network)
                .get(car.getId());

        assertThat(pose).isNotNull();
        assertThat(pose.inJunction()).isTrue();
        assertThat(pose.headingDegrees()).isCloseTo(90.0, within(1.0));
        assertThat(pose.longitude()).isLessThan(106.710);
    }

    @Test
    void theHeadingTurnsOnceTheVehicleIsOnTheSecondRoad() {
        driveUntilOn("BC");

        VehiclePose pose = resolver.resolve(car, network).orElseThrow();

        assertThat(pose.roadId()).isEqualTo("BC");
        assertThat(pose.headingDegrees()).isCloseTo(90.0, within(1.0));
    }

    @Test
    void aJunctionFrameIdentifiesTheConnectorUsedForRendering() {
        driveUntilWaitingAt("B");

        SimulationFrame.VehicleState state = SimulationFrame.capture(
                "timeline", 1, 1, 1.0, SimulationStatus.RUNNING,
                network, List.of(car), List.of(), List.of()).vehicles().get(0);

        assertThat(state.inJunction()).isTrue();
        assertThat(state.junctionFromRoadId()).isEqualTo("AB");
        assertThat(state.junctionToRoadId()).isEqualTo("BC");
    }

    @Test
    void outerLanePoseIsOffsetFromTheRoadCentreline() {
        RoadNetwork multilane = new RoadNetwork();
        multilane.addIntersection(junction("A", 10.770, 106.700));
        multilane.addIntersection(junction("B", 10.780, 106.700));
        multilane.addRoad(new Road("AB", "Broad road", "AB", "A", "B", 1.1, 50, 100, 0,
                3, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.770, 106.700), new GeoPoint(10.780, 106.700)),
                RoadClass.PRIMARY));
        Car outerLaneCar = new Car("outer", "A", "B");
        outerLaneCar.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0)), 1.1, 1.0, 1.0));
        TrafficState state = TrafficState.capture(0, multilane, List.of(outerLaneCar));
        outerLaneCar.planJunctionApproach(multilane, state).ifPresent(state::requestCrossing);
        state.resolveJunctions();
        outerLaneCar.planMovement(multilane, state, 1.0).orElseThrow();

        VehiclePose pose = resolver.resolve(outerLaneCar, multilane).orElseThrow();

        assertThat(pose.laneIndex()).isZero();
        assertThat(pose.lateralOffsetMetres()).isCloseTo(-3.5, within(1e-12));
        assertThat(pose.longitude()).isNotEqualTo(106.700);
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
