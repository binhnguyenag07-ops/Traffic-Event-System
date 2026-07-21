package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.service.RoutingService;

class VehicleTest {

    @Test
    void providesTypeSpecificPhysicalProfiles() {
        Vehicle car = new Car("car-1", "A", "B");
        Vehicle bus = new Bus("bus-1", "A", "B", 40);
        Vehicle emergencyVehicle = new EmergencyVehicle("emergency-1", "A", "B", 2);

        assertThat(car.getLength()).isEqualTo(4.5);
        assertThat(car.getWidth()).isEqualTo(1.8);
        assertThat(car.getMaxSpeed()).isEqualTo(120.0);
        assertThat(car.getCurrentSpeed()).isEqualTo(50.0);
        assertThat(car.getAcceleration()).isEqualTo(2.5);
        assertThat(car.getDeceleration()).isEqualTo(6.5);
        assertThat(car.getVehicleWeight()).isEqualTo(1_500.0);
        assertThat(car.getPriorityLevel()).isZero();

        assertThat(bus.getLength()).isEqualTo(12.0);
        assertThat(bus.getVehicleWeight()).isEqualTo(12_000.0);
        assertThat(bus.getPriorityLevel()).isZero();

        assertThat(emergencyVehicle.getMaxSpeed()).isEqualTo(140.0);
        assertThat(emergencyVehicle.getPriorityLevel()).isEqualTo(2);
    }

    @Test
    void changesSpeedWithinPhysicalBoundsWithoutChangingRouteMovement() {
        Vehicle vehicle = new Car("car-1", "A", "B");

        vehicle.accelerate(2.0);
        assertThat(vehicle.getCurrentSpeed()).isEqualTo(68.0);
        assertThat(vehicle.getMovementSpeed()).isEqualTo(68.0);

        vehicle.decelerate(10.0);
        assertThat(vehicle.getCurrentSpeed()).isZero();

        vehicle.setCurrentSpeed(120.0);
        assertThatThrownBy(() -> vehicle.setCurrentSpeed(120.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current speed must be between zero and the maximum speed.");
    }

    @Test
    void rejectsInvalidPhysicalProfiles() {
        assertThatThrownBy(() -> new VehicleProfile(0.0, 1.8, 120.0, 2.5, 6.5, 1_500.0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Length must be positive.");

        assertThatThrownBy(() -> new Bus("bus-1", "A", "B", 40, VehicleProfile.bus(), 91.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current speed must be between zero and the maximum speed.");

        assertThatThrownBy(() -> new EmergencyVehicle("emergency-1", "A", "B", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Priority level must be positive.");
    }

    @Test
    void requestsRouteAndMovesThroughEveryStep() {
        RoadNetwork network = createNetwork();
        Route route = routeFromAToC();
        RoutingService routingService = (ignoredNetwork, request) ->
                new RouteResult(route, "test", 1, 3);
        Vehicle vehicle = new Car("car-1", "A", "C");

        vehicle.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

        assertThat(vehicle.getCurrentRoute()).isSameAs(route);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.WAITING);

        drive(network, vehicle, 3);
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.MOVING);
        assertThat(vehicle.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isEqualTo(1);

        drive(network, vehicle, 40);
        assertThat(vehicle.getCurrentIntersectionId()).isEqualTo("C");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.ARRIVED);
        assertThat(vehicle.getTravelHistory()).containsExactly("A", "B", "C");
        assertThat(vehicle.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();
        assertThat(network.getRoad("BC").getCurrentVehicleCount()).isZero();
    }


    @Test
    void becomesBlockedWhenRoutingCannotFindAPath() {
        RoadNetwork network = createNetwork();
        RoutingService routingService = (ignoredNetwork, request) -> {
            throw new NoSuchElementException("No route");
        };
        Vehicle vehicle = new Car("car-1", "A", "C");

        vehicle.requestRoute(network, routingService);

        assertThat(vehicle.getCurrentRoute()).isNull();
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BLOCKED);
    }

    @Test
    void doesNotMoveWhenTheNextRoadIsClosed() {
        RoadNetwork network = createNetwork();
        Route route = routeFromAToC();
        Vehicle vehicle = new Car("car-1", "A", "C");
        vehicle.setCurrentRoute(route);
        network.getRoad("AB").getCondition().setRoadStatus(RoadStatus.CLOSED);

        drive(network, vehicle, 1);

        assertThat(vehicle.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.BLOCKED);
        assertThat(vehicle.getOccupiedRoadId()).isNull();
    }

    @Test
    void recomputesRouteFromTheCurrentIntersection() {
        RoadNetwork network = createNetwork();
        Vehicle vehicle = new Car("car-1", "A", "C");
        vehicle.moveTo("B");
        Route route = new Route(
                List.of(new RouteStep("B", "C", "BC", 1.0)),
                1.0,
                1.0,
                1.0);
        RoutingService routingService = (ignoredNetwork, request) -> {
            assertThat(request.getStartIntersectionId()).isEqualTo("B");
            assertThat(request.getDestinationIntersectionId()).isEqualTo("C");
            return new RouteResult(route, "test", 1, 2);
        };

        vehicle.recomputeRoute(network, routingService, RoutePreference.CONGESTION_AWARE);

        assertThat(vehicle.getCurrentRoute()).isSameAs(route);
        assertThat(vehicle.getRoutePreference()).isEqualTo(RoutePreference.CONGESTION_AWARE);
        assertThat(vehicle.getNextRouteStepIndex()).isZero();
    }

    /** Runs the microscopic model for one vehicle, as the engine would. */
    private void drive(RoadNetwork network, Vehicle vehicle, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            TrafficState state = TrafficState.capture(tick, network, List.of(vehicle));
            vehicle.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
            state.resolveJunctions();
            vehicle.advance(network, state, 10.0);
        }
    }

    private RoadNetwork createNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 2, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BC", "B", "C"));
        return network;
    }

    private Road road(String id, String from, String to) {
        return new Road(
                id,
                from,
                to,
                1.0,
                50.0,
                10,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }

    private Route routeFromAToC() {
        return new Route(
                List.of(
                        new RouteStep("A", "B", "AB", 1.0),
                        new RouteStep("B", "C", "BC", 1.0)),
                2.0,
                2.0,
                2.0);
    }
}
