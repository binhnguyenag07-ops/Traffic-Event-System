package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.MovementPlan;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoutingService;
import team.traffic.service.impl.RoutingServiceImpl;

/**
 * The tick loop itself: recovering blocked vehicles, and republishing road
 * density from the space traffic physically takes up.
 */
class TickProcessorTest {

    @Test
    void anOnRoadVehicleReroutesAroundAForbiddenUpcomingMovement() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 2, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("D", "D", 1, 1, LocationType.NORMAL));
        network.addRoad(openRoad("AB", "A", "B"));
        network.addRoad(openRoad("BC", "B", "C"));
        network.addRoad(openRoad("BD", "B", "D"));
        network.addRoad(openRoad("DC", "D", "C"));
        network.getMovementGraph().register(new JunctionMovement(
                "B",
                Optional.of("AB"),
                "BC",
                TurnDirection.STRAIGHT,
                MovementPermission.ALLOWED,
                Set.of(VehicleType.MOTORCYCLE),
                0,
                MovementProvenance.EXPLICIT));
        Car vehicle = new Car("car-on-road-restricted", "A", "C");
        vehicle.setCurrentRoute(new Route(
                List.of(
                        new RouteStep("A", "B", "AB", 1.0),
                        new RouteStep("B", "C", "BC", 1.0)),
                2.0, 2.0, 2.0));
        placeOnRoad(network, List.of(vehicle), vehicle, "AB", 960.0, 0.0);
        TickProcessor processor = new TickProcessor(dijkstra());

        processor.process(0, network, List.of(vehicle), List.of());

        assertThat(vehicle.getCurrentRoute().getSteps())
                .extracting(RouteStep::getRoadId)
                .containsExactly("AB", "BD", "DC");
        assertThat(vehicle.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.WAITING);
    }

    @Test
    void aVehicleBlockedByAnIllegalMovementForItsTypeIsRerouted() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("B", "B", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 2, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("D", "D", 1, 1, LocationType.NORMAL));
        network.addRoad(openRoad("BC", "B", "C"));
        network.addRoad(openRoad("BD", "B", "D"));
        network.addRoad(openRoad("DC", "D", "C"));
        network.getMovementGraph().register(new JunctionMovement(
                "B",
                Optional.empty(),
                "BC",
                TurnDirection.STRAIGHT,
                MovementPermission.ALLOWED,
                Set.of(VehicleType.MOTORCYCLE),
                0,
                MovementProvenance.EXPLICIT));
        Car vehicle = new Car("car-restricted", "B", "C");
        vehicle.setCurrentRoute(new Route(
                List.of(new RouteStep("B", "C", "BC", 1.0)),
                1.0, 1.0, 1.0));
        TickProcessor processor = new TickProcessor(dijkstra());

        processor.process(0, network, List.of(vehicle), List.of());
        processor.process(1, network, List.of(vehicle), List.of());

        assertThat(vehicle.getCurrentRoute().getSteps())
                .extracting(RouteStep::getRoadId)
                .containsExactly("BD", "DC");
        assertThat(vehicle.getOccupiedRoadId()).isEqualTo("BD");
        assertThat(vehicle.getStatus()).isIn(VehicleStatus.MOVING, VehicleStatus.WAITING);
    }

    @Test
    void aVehicleBlockedByAClosedRoadIsReroutedAndMovesOnTheAlternative() {
        RoadNetwork network = networkWithAlternativeRoute();
        TickProcessor processor = new TickProcessor(dijkstra());
        Vehicle vehicle = new Car("car-1", "A", "C");
        vehicle.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0), new RouteStep("B", "C", "BC", 1.0)),
                2.0, 2.0, 2.0));
        network.getRoad("AB").getCondition().setRoadStatus(RoadStatus.CLOSED);

        processor.process(0, network, List.of(vehicle), List.of());
        processor.process(1, network, List.of(vehicle), List.of());

        assertThat(vehicle.getOccupiedRoadId()).isEqualTo("AD");
        assertThat(vehicle.getStatus()).isIn(VehicleStatus.MOVING, VehicleStatus.WAITING);
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();
        assertThat(network.getRoad("AD").getCurrentVehicleCount()).isEqualTo(1);
    }

    @Test
    void roadDensityIsRepublishedFromTheSpaceTrafficActuallyTakesUp() {
        // A 100 m road with capacity 100: three cars are 3% of the headcount
        // but 21% of the tarmac.
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        TickProcessor processor = new TickProcessor(dijkstra());
        List<Vehicle> vehicles = new ArrayList<>();
        for (int index = 0; index < 3; index++) {
            Car car = new Car("car-" + index, "A", "B");
            car.setCurrentRoute(new Route(List.of(new RouteStep("A", "B", "AB", 1.0)), 0.1, 1.0, 1.0));
            vehicles.add(car);
        }

        processor.process(0, network, vehicles, List.of());

        Road road = network.getRoad("AB");
        double headcountRatio = (double) road.getCurrentVehicleCount() / road.getCapacity();
        assertThat(road.getCondition().getVehicleDensity()).isGreaterThan(headcountRatio * 5);
    }

    @Test
    void aProcessorCannotBeBuiltWithoutADynamicsEngine() {
        assertThatThrownBy(() -> new TickProcessor(dijkstra(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dynamics engine");
    }

    @Test
    void unavoidableRearEndCollisionCreatesAccidentStopsBothVehiclesAndLaterReleasesThem() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("R", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        Car leader = carOnRoute("leader");
        Car follower = carOnRoute("follower");
        List<Vehicle> vehicles = new ArrayList<>(List.of(leader, follower));
        placeOnRoad(network, vehicles, leader, 46.0, 0.0);
        placeOnRoad(network, vehicles, follower, 40.0, 20.0);
        List<team.traffic.domain.event.TrafficEvent> events = new ArrayList<>();
        TickProcessor processor = new TickProcessor(dijkstra());

        processor.process(0, 0, network, vehicles, events, 1.0);

        assertThat(events).singleElement().isInstanceOfSatisfying(AccidentEvent.class, accident -> {
            assertThat(accident.getCausingVehicleId()).isEqualTo("follower");
            assertThat(accident.getInvolvedVehicleIds()).containsExactlyInAnyOrder("leader", "follower");
        });
        assertThat(leader.getStatus()).isEqualTo(VehicleStatus.STOPPED);
        assertThat(follower.getStatus()).isEqualTo(VehicleStatus.STOPPED);

        processor.process(301, 301, network, vehicles, events, 1.0);

        assertThat(leader.getStatus()).isNotEqualTo(VehicleStatus.STOPPED);
        assertThat(follower.getStatus()).isNotEqualTo(VehicleStatus.STOPPED);
    }

    @Test
    void disabledAutomaticCollisionPolicyDoesNotInventAnAccidentEvent() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("R", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        Car leader = carOnRoute("leader");
        Car follower = carOnRoute("follower");
        List<Vehicle> vehicles = new ArrayList<>(List.of(leader, follower));
        placeOnRoad(network, vehicles, leader, 46.0, 0.0);
        placeOnRoad(network, vehicles, follower, 40.0, 20.0);
        List<team.traffic.domain.event.TrafficEvent> events = new ArrayList<>();
        TickProcessor processor = new TickProcessor(
                dijkstra(),
                null,
                new VehicleDynamicsEngine(),
                AdaptiveTrafficConfig.disabled(),
                0,
                false);

        processor.process(0, 0, network, vehicles, events, 1.0);

        assertThat(events).isEmpty();
        assertThat(leader.getStatus()).isNotEqualTo(VehicleStatus.STOPPED);
        assertThat(follower.getStatus()).isNotEqualTo(VehicleStatus.STOPPED);
    }

    @Test
    void vehicleRemainsStoppedUntilEveryOverlappingFailureIsResolved() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("R", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        Car car = carOnRoute("car");
        List<Vehicle> vehicles = new ArrayList<>(List.of(car));
        placeOnRoad(network, vehicles, car, 40.0, 10.0);
        List<team.traffic.domain.event.TrafficEvent> events = new ArrayList<>(List.of(
                new BreakdownEvent("short", "R", 0, 5, "car", 0.4, java.util.Set.of(0)),
                new BreakdownEvent("long", "R", 0, 10, "car", 0.4, java.util.Set.of(0))));
        TickProcessor processor = new TickProcessor(dijkstra());

        processor.process(0, 0, network, vehicles, events, 1.0);
        processor.process(6, 6, network, vehicles, events, 1.0);
        assertThat(car.getStatus()).isEqualTo(VehicleStatus.STOPPED);
        assertThat(network.getRoad("R").getCondition().getCongestionLevel()).isEqualTo(0.2);

        processor.process(11, 11, network, vehicles, events, 1.0);
        assertThat(car.getStatus()).isNotEqualTo(VehicleStatus.STOPPED);
        assertThat(network.getRoad("R").getCondition().getCongestionLevel()).isZero();
    }

    @Test
    void aLogicalTickIsSplitAtExactEventSecondBoundaries() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("R", "A", "B", 0.1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        List<Double> stepDurations = new ArrayList<>();
        List<RoadStatus> statusesDuringStep = new ArrayList<>();
        VehicleDynamicsEngine recordingEngine = new VehicleDynamicsEngine() {
            @Override
            public TrafficState step(
                    long tick,
                    double elapsedSeconds,
                    RoadNetwork roadNetwork,
                    List<Vehicle> vehicles,
                    List<team.traffic.domain.event.TrafficEvent> events,
                    List<team.traffic.domain.signal.TrafficSignal> signals,
                    double simulationSeconds) {
                stepDurations.add(simulationSeconds);
                statusesDuringStep.add(roadNetwork.getRoad("R").getCondition().getRoadStatus());
                return null;
            }
        };
        TickProcessor processor = new TickProcessor(dijkstra(), null, recordingEngine);
        RoadClosureEvent closure = new RoadClosureEvent("closure", "R", 2.0, 3.0);

        processor.process(0, 0.0, network, List.of(), new ArrayList<>(List.of(closure)), 10.0);

        assertThat(stepDurations).containsExactly(2.0, 3.0, 5.0);
        assertThat(statusesDuringStep).containsExactly(RoadStatus.OPEN, RoadStatus.CLOSED, RoadStatus.OPEN);
        assertThat(closure.getStatus()).isEqualTo(team.traffic.domain.event.TrafficEventStatus.RESOLVED);
    }

    private static RoutingService dijkstra() {
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        return new RoutingServiceImpl(registry);
    }

    private static Car carOnRoute(String id) {
        Car car = new Car(id, "A", "B");
        car.setCurrentRoute(new Route(List.of(new RouteStep("A", "B", "R", 1.0)), 0.1, 1.0, 1.0));
        return car;
    }

    private static void placeOnRoad(
            RoadNetwork network,
            List<Vehicle> vehicles,
            Vehicle vehicle,
            double progressMetres,
            double speedMps) {
        placeOnRoad(network, vehicles, vehicle, "R", progressMetres, speedMps);
    }

    private static void placeOnRoad(
            RoadNetwork network,
            List<Vehicle> vehicles,
            Vehicle vehicle,
            String roadId,
            double progressMetres,
            double speedMps) {
        TrafficState state = TrafficState.capture(0, network, vehicles);
        vehicle.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
        state.resolveJunctions();
        vehicle.planMovement(network, state, 1.0).orElseThrow();
        vehicle.commitMovement(
                new MovementPlan(roadId, speedMps, progressMetres, false),
                network,
                state);
    }

    /** A to C the direct way through B, or the long way round through D. */
    private static RoadNetwork networkWithAlternativeRoute() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 2, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("D", "D", 1, 1, LocationType.NORMAL));
        network.addRoad(openRoad("AB", "A", "B"));
        network.addRoad(openRoad("BC", "B", "C"));
        network.addRoad(openRoad("AD", "A", "D"));
        network.addRoad(openRoad("DC", "D", "C"));
        return network;
    }

    private static Road openRoad(String id, String from, String to) {
        return new Road(id, from, to, 1.0, 50.0, 10, 0, new RoadCondition(0, 0, RoadStatus.OPEN));
    }

}
