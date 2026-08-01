package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Bus;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/**
 * Behaviour of the microscopic layer as a whole: vehicles queue behind one
 * another, a road fills according to the space its traffic takes up rather than
 * a headcount, and a blocked road backs traffic up behind it.
 */
class VehicleDynamicsEngineTest {

    private static final double QUEUE_ROAD_KM = 0.1;
    private static final double SECONDS_PER_TICK = 10.0;
    private static final BiFunction<String, String, Vehicle> CAR_FACTORY =
            (id, origin) -> new Car(id, origin, "C");
    private static final BiFunction<String, String, Vehicle> BUS_FACTORY =
            (id, origin) -> new Bus(id, origin, "C", 40);

    @Test
    void aRoadHoldsFarFewerBusesThanCarsBecauseTheyTakeUpMoreSpace() {
        int carsHeld = fillBlockedRoad(CAR_FACTORY);
        int busesHeld = fillBlockedRoad(BUS_FACTORY);

        assertThat(carsHeld).isGreaterThan(busesHeld);
        // A 100 m road: cars need 7 m each, buses 15 m.
        assertThat(carsHeld).isBetween(10, 14);
        assertThat(busesHeld).isBetween(4, 7);
    }

    @Test
    void aRoadFillsUpLongBeforeItsNominalVehicleCapacityIsReached() {
        Fixture fixture = new Fixture();
        // Capacity is 100 vehicles, but only 100 m of tarmac.
        fixture.spawn(30, CAR_FACTORY);

        fixture.run(60);

        Road queueRoad = fixture.network.getRoad("AB");
        assertThat(queueRoad.getCurrentVehicleCount()).isLessThan(queueRoad.getCapacity());
        assertThat(fixture.state.physicalJamRatioOf("AB")).isGreaterThan(0.8);
    }

    @Test
    void vehiclesQueueBehindOneAnotherInsteadOfSharingAPosition() {
        Fixture fixture = new Fixture();
        fixture.spawn(6, CAR_FACTORY);

        fixture.run(40);

        List<Double> positions = fixture.vehicles.stream()
                .filter(vehicle -> "AB".equals(vehicle.getOccupiedRoadId()))
                .map(Vehicle::getRoadProgress)
                .sorted()
                .toList();
        assertThat(positions).hasSizeGreaterThan(1);
        for (int index = 1; index < positions.size(); index++) {
            assertThat(positions.get(index) - positions.get(index - 1)).isGreaterThan(0.0);
        }
    }

    @Test
    void trafficStopsAtTheJunctionWhenTheRoadBeyondIsClosed() {
        Fixture fixture = new Fixture();
        fixture.spawn(4, CAR_FACTORY);

        fixture.run(60);

        // Nobody slipped onto the closed road, and the leader is held at its end.
        assertThat(fixture.network.getRoad("BC").getCurrentVehicleCount()).isZero();
        assertThat(fixture.vehicles.stream().map(Vehicle::getRoadProgress).max(Double::compare))
                .hasValueSatisfying(progress -> assertThat(progress).isGreaterThan(0.9));
    }

    @Test
    void aQueueingVehicleReportsAnActualSpeedBelowTheSpeedItWants() {
        Fixture fixture = new Fixture();
        fixture.spawn(8, CAR_FACTORY);

        fixture.run(60);

        Vehicle leader = fixture.vehicles.stream()
                .filter(vehicle -> "AB".equals(vehicle.getOccupiedRoadId()))
                .max((left, right) -> Double.compare(left.getRoadProgress(), right.getRoadProgress()))
                .orElseThrow();
        assertThat(leader.getInstantaneousSpeedKph()).isLessThan(leader.getCruisingSpeedKph());
        assertThat(leader.getWaitingTicks()).isPositive();
    }

    @Test
    void theOutcomeDoesNotDependOnTheOrderOfTheVehicleList() {
        Fixture ascending = new Fixture();
        ascending.spawn(6, CAR_FACTORY);
        ascending.run(30);

        Fixture descending = new Fixture();
        descending.spawn(6, CAR_FACTORY);
        java.util.Collections.reverse(descending.vehicles);
        descending.run(30);

        // Planning every vehicle before moving any, and holding the chosen
        // speed in the plan rather than writing it straight back, means a
        // vehicle reads its leader unmoved and at its old speed however the
        // list is ordered. The result is identical to the last bit.
        assertThat(progressById(descending)).isEqualTo(progressById(ascending));
    }

    @Test
    void aTickIsBrokenIntoSubStepsShortEnoughForTheCarFollowingModel() {
        assertThat(VehicleDynamicsEngine.subStepCount(10.0)).isEqualTo(10);
        assertThat(VehicleDynamicsEngine.subStepCount(0.5)).isEqualTo(1);
        assertThat(VehicleDynamicsEngine.subStepCount(3600.0))
                .isEqualTo(VehicleDynamicsEngine.MAX_SUB_STEPS_PER_TICK);
    }

    private static Map<String, Double> progressById(Fixture fixture) {
        Map<String, Double> byId = new TreeMap<>();
        fixture.vehicles.forEach(vehicle -> byId.put(vehicle.getId(), vehicle.getRoadProgress()));
        return byId;
    }

    private int fillBlockedRoad(BiFunction<String, String, Vehicle> factory) {
        Fixture fixture = new Fixture();
        fixture.spawn(30, factory);
        fixture.run(60);
        return fixture.network.getRoad("AB").getCurrentVehicleCount();
    }

    /**
     * A short road leading to a closed one, so arriving traffic has nowhere to
     * go and has to queue on the approach.
     */
    private static final class Fixture {
        private final RoadNetwork network = new RoadNetwork();
        private final List<Vehicle> vehicles = new ArrayList<>();
        private final VehicleDynamicsEngine engine = new VehicleDynamicsEngine();
        private TrafficState state;

        private Fixture() {
            network.addIntersection(new Intersection("A", "A", 0, 0));
            network.addIntersection(new Intersection("B", "B", 1, 0));
            network.addIntersection(new Intersection("C", "C", 2, 0));
            network.addRoad(new Road("AB", "A", "B", QUEUE_ROAD_KM, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.OPEN)));
            network.addRoad(new Road("BC", "B", "C", 1.0, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.CLOSED)));
        }

        private void spawn(int count, BiFunction<String, String, Vehicle> factory) {
            for (int index = 0; index < count; index++) {
                Vehicle vehicle = factory.apply("v-" + String.format("%02d", index), "A");
                vehicle.setCurrentRoute(new Route(
                        List.of(
                                new RouteStep("A", "B", "AB", 1.0),
                                new RouteStep("B", "C", "BC", 1.0)),
                        QUEUE_ROAD_KM + 1.0,
                        1.0,
                        1.0));
                vehicles.add(vehicle);
            }
        }

        private void run(int ticks) {
            for (int tick = 0; tick < ticks; tick++) {
                state = engine.step(tick, network, vehicles, SECONDS_PER_TICK);
            }
        }
    }
}
