package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;

class DefaultLocalPerceptionServiceTest {
    private final DefaultLocalPerceptionService service =
            new DefaultLocalPerceptionService();

    @Test
    void reportsOverlappingLeaderAndClosestForwardObstruction() {
        RoadNetwork network = network(1);
        Car leader = vehicle("leader");
        enterAndAdvance(leader, network, List.of(leader), 0, 3.0);
        Car follower = vehicle("follower");
        enterAndAdvance(follower, network, List.of(leader, follower), 1, 1.0);
        BreakdownEvent near = obstruction("near", 0.12);
        BreakdownEvent far = obstruction("far", 0.16);
        near.apply(network);
        far.apply(network);
        TrafficState state = TrafficState.capture(
                2,
                4.0,
                network,
                List.of(leader, follower),
                List.of(far, near),
                new PriorityRightOfWayPolicy(),
                new GapAcceptanceModel(),
                new RoadDataRoadLayout());

        LocalPerception perception = service.observe(follower, state);

        assertThat(perception.leader()).isPresent()
                .get()
                .extracting(LeaderObservation::vehicleId)
                .isEqualTo("leader");
        assertThat(perception.reachableLanes()).hasSize(1);
        assertThat(perception.reachableLanes().get(0).obstruction())
                .isPresent()
                .get()
                .extracting(ObstructionObservation::eventId)
                .isEqualTo("near");
    }

    @Test
    void exposesOnlyCurrentAndImmediatelyAdjacentLanes() {
        RoadNetwork network = network(4);
        Car vehicle = vehicle("car");
        enterAndAdvance(vehicle, network, List.of(vehicle), 0, 1.0);
        TrafficState state = TrafficState.capture(
                1, 1.0, network, List.of(vehicle), List.of(),
                new PriorityRightOfWayPolicy(),
                new GapAcceptanceModel(),
                new RoadDataRoadLayout());

        LocalPerception perception = service.observe(vehicle, state);

        assertThat(vehicle.getLaneIndex()).isZero();
        assertThat(perception.reachableLanes())
                .extracting(LaneObservation::laneIndex)
                .containsExactly(0, 1);
    }

    private static BreakdownEvent obstruction(String id, double position) {
        return new BreakdownEvent(
                id,
                "AB",
                0.0,
                100.0,
                "broken-" + id,
                position,
                Set.of(0));
    }

    private static void enterAndAdvance(
            Car vehicle,
            RoadNetwork network,
            List<Car> vehicles,
            long tick,
            double seconds) {
        TrafficState state = TrafficState.capture(tick, network, vehicles);
        vehicle.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
        state.resolveJunctions();
        vehicle.advance(network, state, seconds);
    }

    private static Car vehicle(String id) {
        Car vehicle = new Car(id, "A", "B");
        vehicle.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0)),
                0.2,
                0.01,
                1.0));
        return vehicle;
    }

    private static RoadNetwork network(int laneCount) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection(
                "A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection(
                "B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road(
                "AB",
                "AB",
                "physical-AB",
                "A",
                "B",
                0.2,
                50.0,
                100,
                0,
                laneCount,
                3.5,
                new RoadCondition(0.0, 0.0, RoadStatus.OPEN),
                List.of(),
                RoadClass.RESIDENTIAL));
        return network;
    }
}
