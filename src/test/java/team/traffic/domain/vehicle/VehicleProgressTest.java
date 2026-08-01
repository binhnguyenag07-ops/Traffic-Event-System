package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/**
 * A single vehicle on an empty road, with nothing in its way. Its progress
 * should follow the road's speed limit and the time that passes.
 */
class VehicleProgressTest {

    private static final double SECONDS_PER_TICK = 10.0;

    @Test
    void anUnobstructedVehicleTravelsAtTheLowerOfItsOwnSpeedAndTheLimit() {
        RoadNetwork network = networkWithOneRoadOf(1.0, 50.0);
        Car car = carOn();

        drive(network, car, 1);

        // The car wants 50 km/h and the road allows 50, so ten seconds covers
        // 139 m of the 1 km road. Driver imperfection may shave a little off,
        // but it can never exceed the free-flow figure.
        assertThat(car.getRoadProgress()).isGreaterThan(0.10).isLessThanOrEqualTo(0.1389);
        assertThat(car.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isEqualTo(1);
    }

    @Test
    void aSlowerRoadHoldsTheVehicleBackEvenWhenItCouldGoFaster() {
        RoadNetwork fast = networkWithOneRoadOf(1.0, 50.0);
        RoadNetwork slow = networkWithOneRoadOf(1.0, 20.0);
        Car onFast = carOn();
        Car onSlow = carOn();

        drive(fast, onFast, 3);
        drive(slow, onSlow, 3);

        assertThat(onSlow.getRoadProgress()).isLessThan(onFast.getRoadProgress());
    }

    @Test
    void progressAccumulatesUntilTheVehicleRunsOffTheEndAndArrives() {
        RoadNetwork network = networkWithOneRoadOf(1.0, 50.0);
        Car car = carOn();

        double previous = 0;
        for (int tick = 0; tick < 6; tick++) {
            drive(network, car, 1);
            assertThat(car.getRoadProgress()).isGreaterThan(previous);
            previous = car.getRoadProgress();
            assertThat(car.getCurrentIntersectionId()).isEqualTo("A");
        }

        drive(network, car, 10);

        assertThat(car.getCurrentIntersectionId()).isEqualTo("B");
        assertThat(car.getStatus()).isEqualTo(VehicleStatus.ARRIVED);
        assertThat(car.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();
    }

    @Test
    void theDistanceRecordedMatchesTheLengthOfTheRoadItCovered() {
        RoadNetwork network = networkWithOneRoadOf(1.0, 50.0);
        Car car = carOn();

        drive(network, car, 20);

        assertThat(car.getStatus()).isEqualTo(VehicleStatus.ARRIVED);
        assertThat(car.getDistanceTravelledKm()).isCloseTo(1.0, within(1e-6));
    }

    @Test
    void congestionRaisedByAnEventSlowsTheVehicleDown() {
        RoadNetwork clear = networkWithOneRoadOf(1.0, 50.0);
        RoadNetwork congested = networkWithOneRoadOf(1.0, 50.0);
        congested.getRoad("AB").getCondition().setCongestionLevel(0.8);
        Car onClear = carOn();
        Car onCongested = carOn();

        drive(clear, onClear, 3);
        drive(congested, onCongested, 3);

        assertThat(onCongested.getRoadProgress()).isLessThan(onClear.getRoadProgress());
    }

    private static Car carOn() {
        Car car = new Car("car-1", "A", "B");
        car.setCurrentRoute(new Route(List.of(new RouteStep("A", "B", "AB", 1.0)), 1.0, 0.02, 1.0));
        return car;
    }

    /** Runs the microscopic model for one vehicle, as the engine would. */
    private static void drive(RoadNetwork network, Car car, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            TrafficState state = TrafficState.capture(tick, network, List.of(car));
            car.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
            state.resolveJunctions();
            car.advance(network, state, SECONDS_PER_TICK);
        }
    }

    private static RoadNetwork networkWithOneRoadOf(double distanceKm, double speedLimitKph) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addRoad(new Road("AB", "A", "B", distanceKm, speedLimitKph, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        return network;
    }
}
