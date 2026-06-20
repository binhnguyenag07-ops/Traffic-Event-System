package team.traffic.observer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;

class TrafficEventBusTest {

    @Test
    void publishesToSubscribedListenersOnly() {
        TrafficEventBus eventBus = new TrafficEventBus();
        List<TrafficEvent> receivedEvents = new ArrayList<>();
        TrafficEventListener listener = receivedEvents::add;
        TrafficEvent event = new RoadClosureEvent("event-1", "AB", 0, 2);

        eventBus.subscribe(listener);
        eventBus.publish(event);
        eventBus.unsubscribe(listener);
        eventBus.publish(event);

        assertThat(receivedEvents).containsExactly(event);
        assertThat(eventBus.getListeners()).isEmpty();
    }

    @Test
    void ignoresNullAndDuplicateSubscriptions() {
        TrafficEventBus eventBus = new TrafficEventBus();
        TrafficEventListener listener = event -> { };

        eventBus.subscribe(null);
        eventBus.subscribe(listener);
        eventBus.subscribe(listener);

        assertThat(eventBus.getListeners()).containsExactly(listener);
    }
}
