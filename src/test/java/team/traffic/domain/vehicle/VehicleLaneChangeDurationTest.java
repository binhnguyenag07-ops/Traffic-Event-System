package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.dynamics.GapAcceptanceModel;
import team.traffic.domain.vehicle.dynamics.PriorityRightOfWayPolicy;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.dynamics.TrafficState;

/**
 * A lane change is a manoeuvre that takes time, not an assignment that takes
 * none. These tests pin the two things that follow from that: a vehicle is
 * somewhere real while it is between lanes, and it gets there at a speed rather
 * than instantly.
 *
 * <p>The manoeuvre is provoked with a lane-blocking accident rather than a slow
 * leader, because a vehicle joining a road picks the emptiest lane and so would
 * never queue up behind one.</p>
 */
class VehicleLaneChangeDurationTest {
    private static final double LANE_WIDTH_METRES = 3.5;
    private static final double TIME_STEP_SECONDS = 1.0;
    private static final double ROAD_LENGTH_KM = 3.0;

    @Test
    void entersAMultiLaneRoadAlignedWithItsLaneCentre() {
        RoadNetwork network = network(3);
        Vehicle car = car("car-1");

        drive(network, List.of(car), List.of(), 2);

        assertThat(car.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(car.getLaneIndex()).isZero();
        // Outermost of three lanes: one full lane width from the centreline.
        assertThat(car.getLateralOffsetMetres()).isCloseTo(-LANE_WIDTH_METRES, within(1e-12));
    }

    @Test
    void steeringAroundABlockedLaneSlidesAcrossInsteadOfJumping() {
        RoadNetwork network = network(2);
        List<TrafficEvent> blockages = laneBlockages(network);
        Vehicle car = car("car-1");

        List<Double> offsets = new ArrayList<>();
        List<Integer> lanes = new ArrayList<>();
        for (int tick = 0; tick < 100; tick++) {
            driveOnce(network, List.of(car), blockages, tick);
            if ("AB".equals(car.getOccupiedRoadId())) {
                offsets.add(car.getLateralOffsetMetres());
                lanes.add(car.getLaneIndex());
            }
        }

        assertThat(lanes)
                .as("the car joins the lane that is clear at the entry")
                .startsWith(0);
        assertThat(lanes)
                .as("and moves out of it once its own lane is blocked ahead")
                .contains(1);

        // This vehicle's own limit, not the type default: every driver gets a
        // stable variation around it.
        double maxLateralStep = car.getDriverProfile().maxLateralSpeedMps() * TIME_STEP_SECONDS;
        for (int i = 1; i < offsets.size(); i++) {
            assertThat(Math.abs(offsets.get(i) - offsets.get(i - 1)))
                    .as("sideways movement between two steps must respect the lateral speed")
                    .isLessThanOrEqualTo(maxLateralStep + 1e-9);
        }

        assertThat(offsets)
                .as("the car is genuinely between the two lane centres at some point")
                .anyMatch(offset -> offset > -LANE_WIDTH_METRES / 2 + 1e-9
                        && offset < LANE_WIDTH_METRES / 2 - 1e-9);
    }

    @Test
    void crossingALaneTakesMoreThanOneStep() {
        RoadNetwork network = network(2);
        List<TrafficEvent> blockages = laneBlockages(network);
        Vehicle car = car("car-1");

        int stepsBetweenLaneCentres = 0;
        for (int tick = 0; tick < 100; tick++) {
            driveOnce(network, List.of(car), blockages, tick);
            double offset = car.getLateralOffsetMetres();
            if ("AB".equals(car.getOccupiedRoadId())
                    && Math.abs(offset) < LANE_WIDTH_METRES / 2 - 1e-9) {
                stepsBetweenLaneCentres++;
            }
        }

        // 3.5 metres at 1 m/s in one-second steps cannot be done in one.
        assertThat(stepsBetweenLaneCentres).isGreaterThanOrEqualTo(2);
    }

    @Test
    void aSettledVehicleSitsExactlyOnItsLaneCentre() {
        RoadNetwork network = network(2);
        List<TrafficEvent> blockages = laneBlockages(network);
        Vehicle car = car("car-1");

        Double lastOnRoad = null;
        for (int tick = 0; tick < 100; tick++) {
            driveOnce(network, List.of(car), blockages, tick);
            if ("AB".equals(car.getOccupiedRoadId()) && car.getLaneIndex() == 1) {
                lastOnRoad = car.getLateralOffsetMetres();
            }
        }

        assertThat(lastOnRoad)
                .as("once the change is complete the car sits on the outer lane centre")
                .isNotNull()
                .isEqualTo(LANE_WIDTH_METRES / 2);
    }

    /**
     * Blocks the outer lane close to the entry and the inner lane much further
     * on. The first keeps the joining vehicle out of the outer lane; the second
     * is what it later has to steer around.
     */
    private List<TrafficEvent> laneBlockages(RoadNetwork network) {
        AccidentEvent nearOuterLane = new AccidentEvent(
                "near", "AB", 0, 100_000, 0.1, "cause-1", 0.05, Set.of(1));
        AccidentEvent farInnerLane = new AccidentEvent(
                "far", "AB", 0, 100_000, 0.1, "cause-2", 0.60, Set.of(0));
        nearOuterLane.apply(network);
        farInnerLane.apply(network);
        return List.of(nearOuterLane, farInnerLane);
    }

    private void drive(RoadNetwork network, List<Vehicle> vehicles, List<TrafficEvent> events, int ticks) {
        for (int tick = 0; tick < ticks; tick++) {
            driveOnce(network, vehicles, events, tick);
        }
    }

    private void driveOnce(
            RoadNetwork network, List<Vehicle> vehicles, List<TrafficEvent> events, int tick) {
        TrafficState state = TrafficState.capture(
                tick, tick, network, vehicles, events,
                new PriorityRightOfWayPolicy(), new GapAcceptanceModel(), new RoadDataRoadLayout());
        for (Vehicle vehicle : vehicles) {
            vehicle.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
        }
        state.resolveJunctions();
        for (Vehicle vehicle : vehicles) {
            vehicle.advance(network, state, TIME_STEP_SECONDS);
        }
    }

    private RoadNetwork network(int laneCount) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "Broad road", "AB", "A", "B", ROAD_LENGTH_KM, 100, 200, 0,
                laneCount, LANE_WIDTH_METRES, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(), RoadClass.PRIMARY));
        return network;
    }

    private Vehicle car(String id) {
        Vehicle car = new Car(id, "A", "B");
        car.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", ROAD_LENGTH_KM)),
                ROAD_LENGTH_KM, ROAD_LENGTH_KM, ROAD_LENGTH_KM));
        return car;
    }
}
