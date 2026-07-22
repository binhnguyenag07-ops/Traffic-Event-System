package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Car;
import team.traffic.service.RoutingService;

class TrafficSimulatorTest {

    private TrafficSimulator simulator;

    @BeforeEach
    void setUp() {
        RoadNetwork network = createNetwork();
        RoutingService routingService = (net, req) -> null;
        simulator = new TrafficSimulator(network, routingService);
    }

    // --- createEvent with explicit values ---

    @Test
    void createCongestionEventWithExplicitValue() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.CONGESTION, "AB", 0, 10, 0.7, null);

        assertThat(event).isInstanceOf(CongestionEvent.class);
        assertThat(event.getAffectedRoadId()).isEqualTo("AB");
        assertThat(event.getStartTimeSeconds()).isEqualTo(0);
        assertThat(event.getDurationSeconds()).isEqualTo(10);
        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.SCHEDULED);
        assertThat(((CongestionEvent) event).getCongestionIncrease()).isEqualTo(0.7);
    }

    @Test
    void createAccidentEventWithExplicitValues() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.ACCIDENT, "AB", 5, 10, 0.8, "car-42");

        assertThat(event).isInstanceOf(AccidentEvent.class);
        assertThat(((AccidentEvent) event).getCapacityReduction()).isEqualTo(0.8);
        assertThat(((AccidentEvent) event).getCausingVehicleId()).isEqualTo("car-42");
    }

    @Test
    void createRoadClosureEvent() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.ROAD_CLOSURE, "AB", 0, 5, null, null);

        assertThat(event).isInstanceOf(RoadClosureEvent.class);
        assertThat(event.getDurationSeconds()).isEqualTo(5);
    }

    // --- createEvent with defaults ---

    @Test
    void createCongestionEventUsesDefaultValueWhenNull() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.CONGESTION, "AB", 0, 10, null, null);

        assertThat(((CongestionEvent) event).getCongestionIncrease()).isEqualTo(0.5);
    }

    @Test
    void createAccidentEventUsesDefaultValues() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.ACCIDENT, "AB", 0, 10, null, null);

        assertThat(((AccidentEvent) event).getCapacityReduction()).isEqualTo(0.5);
        assertThat(((AccidentEvent) event).getCausingVehicleId()).isEqualTo("system");
    }

    // --- convenience overload ---

    @Test
    void createEventConvenienceOverloadUsesDefaults() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.CONGESTION, "AB", 0, 10);

        assertThat(event).isInstanceOf(CongestionEvent.class);
        assertThat(((CongestionEvent) event).getCongestionIncrease()).isEqualTo(0.5);
    }

    // --- addEvent stores in events list ---

    @Test
    void addEventIsRetrievableFromGetEvents() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.ROAD_CLOSURE, "AB", 0, 5);

        simulator.addEvent(event);
        assertThat(simulator.getEvents()).containsExactly(event);
    }

    // --- initialize with EventConfig uses configurable values ---

    @Test
    void initializeBuildsEventsFromConfigWithDefaults() {
        EventConfig config = new EventConfig("ev-1", "CONGESTION", "AB", 0, 10);
        simulator.initialize(List.of(), List.of(config));

        assertThat(simulator.getEvents()).hasSize(1);
        TrafficEvent event = simulator.getEvents().get(0);
        assertThat(event).isInstanceOf(CongestionEvent.class);
        assertThat(((CongestionEvent) event).getCongestionIncrease()).isEqualTo(0.5);
    }

    @Test
    void initializeBuildsEventsFromConfigWithExplicitValues() {
        EventConfig config = new EventConfig("ev-1", "CONGESTION", "AB", 0, 10, 0.8, null);
        simulator.initialize(List.of(), List.of(config));

        assertThat(simulator.getEvents()).hasSize(1);
        TrafficEvent event = simulator.getEvents().get(0);
        assertThat(((CongestionEvent) event).getCongestionIncrease()).isEqualTo(0.8);
    }

    @Test
    void initializeBuildsAccidentEventFromConfigWithCausingVehicle() {
        EventConfig config = new EventConfig("ev-1", "ACCIDENT", "AB", 0, 10, 0.9, "truck-7");
        simulator.initialize(List.of(), List.of(config));

        assertThat(simulator.getEvents()).hasSize(1);
        TrafficEvent event = simulator.getEvents().get(0);
        assertThat(event.getId()).isEqualTo("ev-1");
        assertThat(event).isInstanceOf(AccidentEvent.class);
        assertThat(((AccidentEvent) event).getCapacityReduction()).isEqualTo(0.9);
        assertThat(((AccidentEvent) event).getCausingVehicleId()).isEqualTo("truck-7");
    }

    @Test
    void getSnapshotReturnsCurrentSimulationState() {
        VehicleConfig vehicleConfig = new VehicleConfig("car-1", "CAR", "A", "B");
        EventConfig eventConfig = new EventConfig("ev-1", "ROAD_CLOSURE", "AB", 0, 5);

        simulator.initialize(List.of(vehicleConfig), List.of(eventConfig));

        SimulationSnapshot snapshot = simulator.getSnapshot();
        assertThat(snapshot.tick()).isZero();
        assertThat(snapshot.status()).isEqualTo(SimulationStatus.READY);
        assertThat(snapshot.vehicles()).hasSize(1);
        assertThat(snapshot.vehicles().get(0).getId()).isEqualTo("car-1");
        assertThat(snapshot.trafficEvents()).hasSize(1);
        assertThat(snapshot.trafficEvents().get(0).getId()).isEqualTo("ev-1");
        assertThatThrownBy(() -> snapshot.vehicles().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void advanceTickActivatesScheduledEventWhenStartTimeHasAlreadyPassedButWindowIsOpen() {
        RoadClosureEvent event = new RoadClosureEvent("late-event", "AB", 0, 50);

        simulator.advanceTick();
        simulator.advanceTick();
        simulator.addEvent(event);
        simulator.advanceTick();

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
    }

    @Test
    void removingArrivedVehicleArchivesCompletedTrip() {
        Car vehicle = new Car("car-1", "A", "B");
        simulator.addVehicle(vehicle);
        vehicle.addTravelTime(30);
        vehicle.moveTo("B");

        assertThat(simulator.removeVehicle("car-1")).isTrue();

        assertThat(simulator.getCompletedTrips()).singleElement().satisfies(trip -> {
            assertThat(trip.vehicleId()).isEqualTo("car-1");
            assertThat(trip.vehicleType()).isEqualTo(vehicle.getType());
            assertThat(trip.travelTimeSeconds()).isEqualTo(30);
        });
        assertThatThrownBy(() -> simulator.getCompletedTrips().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void removingVehicleBeforeArrivalDoesNotArchiveTrip() {
        simulator.addVehicle(new Car("car-1", "A", "B"));

        assertThat(simulator.removeVehicle("car-1")).isTrue();

        assertThat(simulator.getCompletedTrips()).isEmpty();
    }

    @Test
    void arrivedVehicleRemainsVisibleBrieflyThenIsRemovedAndArchived() {
        Car vehicle = new Car("car-1", "A", "B");
        simulator.addVehicle(vehicle);
        vehicle.moveTo("B");

        simulator.advanceTick();
        assertThat(simulator.getVehicles()).extracting(vehicleItem -> vehicleItem.getId())
                .containsExactly("car-1");

        simulator.advanceTick();
        simulator.advanceTick();

        assertThat(simulator.getVehicles()).isEmpty();
        assertThat(simulator.getCompletedTrips()).extracting(trip -> trip.vehicleId())
                .containsExactly("car-1");
    }

    @Test
    void resetClearsCompletedTrips() {
        archiveCompletedTrip();

        simulator.reset();

        assertThat(simulator.getCompletedTrips()).isEmpty();
    }

    @Test
    void initializeClearsCompletedTrips() {
        archiveCompletedTrip();

        simulator.initialize(List.of(), List.of());

        assertThat(simulator.getCompletedTrips()).isEmpty();
    }

    @Test
    void replacingNetworkClearsCompletedTrips() {
        archiveCompletedTrip();

        simulator.replaceNetwork(createNetwork());

        assertThat(simulator.getCompletedTrips()).isEmpty();
    }

    // --- vehicle trace timeline ---

    @Test
    void everyTickIsRecordedInTheVehicleTrace() {
        simulator.initialize(List.of(new VehicleConfig("car-1", "CAR", "A", "B")), List.of());
        simulator.advanceTick();
        simulator.advanceTick();

        assertThat(simulator.getVehicleTraceSummary().recordedTickCount()).isEqualTo(3);
        assertThat(simulator.getVehicleTrace("car-1", 0, 2))
                .extracting(point -> point.tick())
                .containsExactly(0L, 1L, 2L);
        assertThat(simulator.hasVehicleTraceAtTick(2)).isTrue();
        assertThat(simulator.getVehicleStatesAtTick(1))
                .singleElement()
                .satisfies(point -> assertThat(point.vehicleId()).isEqualTo("car-1"));
    }

    @Test
    void resetStartsANewTraceAtTickZero() {
        simulator.initialize(List.of(new VehicleConfig("car-1", "CAR", "A", "B")), List.of());
        simulator.advanceTick();
        simulator.advanceTick();

        simulator.reset();

        assertThat(simulator.getVehicleTraceSummary().firstTick()).isZero();
        assertThat(simulator.getVehicleTraceSummary().lastTick()).isZero();
        assertThat(simulator.getVehicleTraceSummary().recordedTickCount()).isEqualTo(1);
        assertThat(simulator.getVehicleTrace("car-1", 0, 10))
                .extracting(point -> point.tick())
                .containsExactly(0L);
    }

    @Test
    void aRecordedPointDoesNotChangeWhenTheVehicleMovesOn() {
        simulator.initialize(List.of(new VehicleConfig("car-1", "CAR", "A", "B")), List.of());
        simulator.advanceTick();

        var atTickOne = simulator.getVehicleStateAtTick("car-1", 1).orElseThrow();
        simulator.advanceTick();

        assertThat(simulator.getVehicleStateAtTick("car-1", 1).orElseThrow()).isEqualTo(atTickOne);
    }

    // --- validation ---

    @Test
    void createEventRejectsNullType() {
        assertThatThrownBy(() -> simulator.createEvent(null, "AB", 0, 5, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
    }

    @Test
    void createEventRejectsNullRoadId() {
        assertThatThrownBy(() -> simulator.createEvent(
                TrafficEventType.CONGESTION, null, 0, 5, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("road id");
    }

    @Test
    void createEventRejectsBlankRoadId() {
        assertThatThrownBy(() -> simulator.createEvent(
                TrafficEventType.CONGESTION, "  ", 0, 5, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("road id");
    }

    // --- helper ---

    private RoadNetwork createNetwork() {
        RoadNetwork net = new RoadNetwork();
        net.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        net.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        net.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0, new RoadCondition(0, 0, RoadStatus.OPEN)));
        return net;
    }

    private void archiveCompletedTrip() {
        Car vehicle = new Car("car-1", "A", "B");
        simulator.addVehicle(vehicle);
        vehicle.moveTo("B");
        simulator.removeVehicle("car-1");
    }
}
