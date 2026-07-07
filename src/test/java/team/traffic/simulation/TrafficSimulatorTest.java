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
import team.traffic.domain.event.TrafficLightDelayEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
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
        assertThat(event.getStartTick()).isEqualTo(0);
        assertThat(event.getDurationTicks()).isEqualTo(10);
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
        assertThat(event.getDurationTicks()).isEqualTo(5);
    }

    @Test
    void createTrafficLightDelayEventWithExplicitValue() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.TRAFFIC_LIGHT_DELAY, "AB", 0, 10, 3.0, null);

        assertThat(event).isInstanceOf(TrafficLightDelayEvent.class);
        assertThat(((TrafficLightDelayEvent) event).getDelayTicks()).isEqualTo(3);
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

    @Test
    void createTrafficLightDelayEventUsesDefaultDelay() {
        TrafficEvent event = simulator.createEvent(
                TrafficEventType.TRAFFIC_LIGHT_DELAY, "AB", 0, 10, null, null);

        assertThat(((TrafficLightDelayEvent) event).getDelayTicks()).isEqualTo(5);
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
        assertThat(event).isInstanceOf(AccidentEvent.class);
        assertThat(((AccidentEvent) event).getCapacityReduction()).isEqualTo(0.9);
        assertThat(((AccidentEvent) event).getCausingVehicleId()).isEqualTo("truck-7");
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
}
