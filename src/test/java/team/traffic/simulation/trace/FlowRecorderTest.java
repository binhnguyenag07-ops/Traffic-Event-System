package team.traffic.simulation.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.MovementPlan;
import team.traffic.domain.vehicle.dynamics.TrafficState;

class FlowRecorderTest {

    @Test
    void evictsOldestSamplesAtTheConfiguredRetentionBoundary() {
        RoadNetwork network = network();
        Car vehicle = carOnRoad(network);
        FlowRecorder recorder = new FlowRecorder(
                new team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout(), 2);

        recorder.record(1, 1, network, List.of(vehicle));
        recorder.record(2, 2, network, List.of(vehicle));
        recorder.record(3, 3, network, List.of(vehicle));

        assertThat(recorder.getSamples())
                .extracting(RoadFlowSample::tick)
                .containsExactly(2L, 3L);
        assertThat(recorder.getSamplesForRoad("AB")).hasSize(2);

        recorder.reset();

        assertThat(recorder.getSamples()).isEmpty();
    }

    private static Car carOnRoad(RoadNetwork network) {
        Car car = new Car("car-1", "A", "B");
        car.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1)),
                1,
                1,
                1));
        List<Vehicle> vehicles = List.of(car);
        TrafficState state = TrafficState.capture(0, network, vehicles);
        car.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
        state.resolveJunctions();
        car.planMovement(network, state, 1).orElseThrow();
        car.commitMovement(new MovementPlan("AB", 10, 10, false), network, state);
        return car;
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addRoad(new Road(
                "AB", "A", "B", 1, 50, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        return network;
    }
}
