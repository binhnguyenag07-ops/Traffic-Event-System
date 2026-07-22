package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
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
import team.traffic.observer.TrafficEventBus;
import team.traffic.observer.TrafficEventListener;
import team.traffic.service.RoadNetworkService;

class TrafficEventServiceImplTest {

    private TrafficEventBus eventBus;
    private TrafficEventServiceImpl service;
    private RoadNetwork network;
    private StubRoadNetworkService roadNetworkService;

    @BeforeEach
    void setUp() {
        eventBus = new TrafficEventBus();
        network = createNetwork(new RoadCondition(0, 0, RoadStatus.OPEN));
        roadNetworkService = new StubRoadNetworkService(network);
        service = new TrafficEventServiceImpl(eventBus, roadNetworkService);
    }

    // --- addEvent ---

    @Test
    void addEventStoresEvent() {
        TrafficEvent event = new RoadClosureEvent("ev-1", "AB", 0, 5);
        service.addEvent(event);
        assertThat(service.getAllEvents()).containsExactly(event);
    }

    @Test
    void addEventIgnoresNull() {
        service.addEvent(null);
        assertThat(service.getAllEvents()).isEmpty();
    }

    @Test
    void addMultipleEventsPreservesOrder() {
        TrafficEvent event1 = new RoadClosureEvent("ev-1", "AB", 0, 5);
        TrafficEvent event2 = new CongestionEvent("ev-2", "AB", 1, 3, 0.3);
        service.addEvent(event1);
        service.addEvent(event2);
        assertThat(service.getAllEvents()).containsExactly(event1, event2);
    }

    // --- getAllEvents ---

    @Test
    void getAllEventsReturnsDefensiveCopy() {
        TrafficEvent event = new RoadClosureEvent("ev-1", "AB", 0, 5);
        service.addEvent(event);

        List<TrafficEvent> returned = service.getAllEvents();
        returned.clear();

        assertThat(service.getAllEvents()).hasSize(1);
    }

    @Test
    void getAllEventsReturnsEmptyListWhenNoEventsAdded() {
        assertThat(service.getAllEvents()).isEmpty();
    }

    // --- getEventById ---

    @Test
    void getEventByIdReturnsEvent() {
        TrafficEvent event = new RoadClosureEvent("ev-1", "AB", 0, 5);
        service.addEvent(event);
        assertThat(service.getEventById("ev-1")).isSameAs(event);
    }

    @Test
    void getEventByIdReturnsNullForUnknownId() {
        assertThat(service.getEventById("unknown")).isNull();
    }

    // --- getActiveEvents ---

    @Test
    void getActiveEventsReturnsOnlyActive() {
        CongestionEvent active = new CongestionEvent("ev-1", "AB", 0, 10, 0.3);
        RoadClosureEvent scheduled = new RoadClosureEvent("ev-2", "AB", 5, 5);
        service.addEvent(active);
        service.addEvent(scheduled);

        service.processEventsForTick(0, network);

        assertThat(service.getActiveEvents()).hasSize(1);
        assertThat(service.getActiveEvents().get(0).getId()).isEqualTo("ev-1");
    }

    @Test
    void anEventWhoseWholeWindowWasSkippedIsResolvedWithoutBeingApplied() {
        RoadClosureEvent expired = new RoadClosureEvent("expired", "AB", 2, 3);
        service.addEvent(expired);

        service.processEventsAt(10, network);

        assertThat(expired.getStatus()).isEqualTo(TrafficEventStatus.RESOLVED);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
    }

    // --- getEventsByRoad ---

    @Test
    void getEventsByRoadFiltersCorrectly() {
        service.addEvent(new RoadClosureEvent("ev-1", "AB", 0, 5));
        service.addEvent(new RoadClosureEvent("ev-2", "CD", 0, 5));
        service.addEvent(new CongestionEvent("ev-3", "AB", 0, 5, 0.3));

        List<TrafficEvent> abEvents = service.getEventsByRoad("AB");
        assertThat(abEvents).hasSize(2);
    }

    // --- removeEvent ---

    @Test
    void removeEventRemovesExistingEvent() {
        service.addEvent(new RoadClosureEvent("ev-1", "AB", 0, 5));
        boolean removed = service.removeEvent("ev-1");
        assertThat(removed).isTrue();
        assertThat(service.getAllEvents()).isEmpty();
    }

    @Test
    void removeEventReturnsFalseForUnknown() {
        assertThat(service.removeEvent("unknown")).isFalse();
    }

    // --- processEventsForTick ---

    @Test
    void processEventsActivatesScheduledEventWhenTickReached() {
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 5, 10, 0.4);
        service.addEvent(event);

        service.processEventsForTick(5, network);

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel())
                .isCloseTo(0.4, within(1e-9));
    }

    @Test
    void processEventsDoesNotActivateEventBeforeStartTick() {
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 5, 10, 0.4);
        service.addEvent(event);

        service.processEventsForTick(4, network);

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.SCHEDULED);
    }

    @Test
    void processEventsResolvesActiveEventWhenDurationExpired() {
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 0, 5, 0.4);
        service.addEvent(event);

        service.processEventsForTick(0, network);
        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);

        service.processEventsForTick(5, network);
        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.RESOLVED);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isEqualTo(0.0);
    }

    @Test
    void processEventsDoesNotResolveActiveEventBeforeDurationExpires() {
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 0, 5, 0.4);
        service.addEvent(event);

        service.processEventsForTick(0, network);
        service.processEventsForTick(4, network);

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
    }

    @Test
    void processEventsPublishesEventOnActivation() {
        RecordingListener listener = new RecordingListener();
        eventBus.subscribe(listener);
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 0, 5, 0.4);
        service.addEvent(event);

        service.processEventsForTick(0, network);

        assertThat(listener.receivedEvents).containsExactly(event);
    }

    @Test
    void processEventsPublishesEventOnResolution() {
        RecordingListener listener = new RecordingListener();
        eventBus.subscribe(listener);
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 0, 5, 0.4);
        service.addEvent(event);

        service.processEventsForTick(0, network);
        service.processEventsForTick(5, network);

        assertThat(listener.receivedEvents).hasSize(2);
        assertThat(listener.receivedEvents.get(0)).isEqualTo(event);
        assertThat(listener.receivedEvents.get(1)).isEqualTo(event);
    }

    @Test
    void processEventsSkipsNullNetwork() {
        CongestionEvent event = new CongestionEvent("ev-1", "AB", 0, 5, 0.4);
        service.addEvent(event);

        service.processEventsForTick(0, null);

        assertThat(event.getStatus()).isEqualTo(TrafficEventStatus.SCHEDULED);
    }

    @Test
    void processEventsHandlesAccidentEvent() {
        RoadNetwork accNetwork = createNetwork(new RoadCondition(0, 0, RoadStatus.OPEN));
        AccidentEvent accident = new AccidentEvent("ev-1", "AB", 0, 5, 0.8, "car-1");
        service.addEvent(accident);
        service.processEventsForTick(0, accNetwork);

        assertThat(accident.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
        assertThat(accNetwork.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
    }

    @Test
    void processEventsHandlesRoadClosureEvent() {
        RoadNetwork clNetwork = createNetwork(new RoadCondition(0, 0, RoadStatus.OPEN));
        RoadClosureEvent closure = new RoadClosureEvent("ev-1", "AB", 0, 5);
        service.addEvent(closure);
        service.processEventsForTick(0, clNetwork);

        assertThat(closure.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
        assertThat(clNetwork.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CLOSED);
    }

    @Test
    void overlappingEventsRebuildRemainingEffectAndRestoreOriginalRoadAtTheEnd() {
        CongestionEvent shortCongestion = new CongestionEvent("short", "AB", 0, 5, 0.4);
        RoadClosureEvent longClosure = new RoadClosureEvent("long", "AB", 0, 10);
        service.addEvent(shortCongestion);
        service.addEvent(longClosure);

        service.processEventsAt(0, network);
        service.processEventsAt(6, network);

        assertThat(shortCongestion.getStatus()).isEqualTo(TrafficEventStatus.RESOLVED);
        assertThat(longClosure.getStatus()).isEqualTo(TrafficEventStatus.ACTIVE);
        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CLOSED);

        service.processEventsAt(11, network);

        assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isZero();
    }

    // --- helper ---

    private RoadNetwork createNetwork(RoadCondition condition) {
        RoadNetwork net = new RoadNetwork();
        net.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        net.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        net.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0, condition));
        return net;
    }

    private static class RecordingListener implements TrafficEventListener {
        final List<TrafficEvent> receivedEvents = new ArrayList<>();

        @Override
        public void onTrafficEvent(TrafficEvent event) {
            receivedEvents.add(event);
        }
    }

    private static class StubRoadNetworkService implements RoadNetworkService {
        private final RoadNetwork network;

        StubRoadNetworkService(RoadNetwork network) {
            this.network = network;
        }

        @Override
        public RoadNetwork getNetwork() {
            return network;
        }

        @Override
        public Road recordVehicleEnteringRoad(String roadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Road recordVehicleLeavingRoad(String roadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double updateVehicleDensity(String roadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getAverageVehicleDensity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Road> findRoadsAtOrAboveDensity(double minimumDensity) {
            throw new UnsupportedOperationException();
        }
    }
}
