package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BusStopTest {
    private RoadNetwork network;

    @BeforeEach
    void setUp() {
        network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 0, 1));
        network.addRoad(road("R1", "A", "B"));
    }

    /** A one-kilometre road, so a position maps to a round number of metres. */
    private static Road road(String id, String from, String to) {
        return new Road(id, from, to, 1.0, 50.0, 40, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.RESIDENTIAL);
    }

    @Test
    void rejectsAPositionOutsideTheRoad() {
        assertThatThrownBy(() -> new BusStop("S1", "Stop", "R1", 1.4, 10.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("within [0, 1]");
    }

    @Test
    void fallsBackToTheIdWhenOpenStreetMapRecordsNoName() {
        assertThat(new BusStop("S1", null, "R1", 0.5, 10.0).name()).isEqualTo("S1");
        assertThat(new BusStop("S1", "  ", "R1", 0.5, 10.0).name()).isEqualTo("S1");
    }

    @Test
    void convertsItsPositionToMetresAlongTheRoad() {
        Road road = network.getRoad("R1");
        assertThat(new BusStop("S1", "Stop", "R1", 0.25, 10.0).distanceFromStartMetres(road))
                .isEqualTo(250.0);
    }

    @Test
    void refusesAStopOnARoadThatDoesNotExist() {
        assertThatThrownBy(() -> network.addBusStop(new BusStop("S1", "Stop", "R9", 0.5, 10.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("road does not exist");
    }

    @Test
    void refusesTwoStopsWithTheSameId() {
        network.addBusStop(new BusStop("S1", "Stop", "R1", 0.2, 10.0));
        assertThatThrownBy(() -> network.addBusStop(new BusStop("S1", "Other", "R1", 0.8, 10.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate bus-stop id");
    }

    @Test
    void ordersStopsByPositionRegardlessOfInsertionOrder() {
        network.addBusStop(new BusStop("S3", "Third", "R1", 0.9, 10.0));
        network.addBusStop(new BusStop("S1", "First", "R1", 0.1, 10.0));
        network.addBusStop(new BusStop("S2", "Second", "R1", 0.5, 10.0));

        // A bus serves stops in the order it drives past them, so the network
        // sorts once here rather than making every sub-step do it.
        assertThat(network.getBusStopsOnRoad("R1"))
                .extracting(BusStop::id)
                .containsExactly("S1", "S2", "S3");
    }

    @Test
    void reportsNoStopsForARoadThatHasNone() {
        assertThat(network.getBusStopsOnRoad("R1")).isEmpty();
        assertThat(network.getBusStopCount()).isZero();
    }

    @Test
    void keepsTheTwoDirectionsOfOneStreetSeparate() {
        network.addRoad(road("R2", "B", "A"));
        network.addBusStop(new BusStop("S1", "Northbound", "R1", 0.4, 10.0));
        network.addBusStop(new BusStop("S2", "Southbound", "R2", 0.6, 10.0));

        // The stop across the street belongs to the other direction; a bus
        // travelling one way must not serve the other way's kerb.
        assertThat(network.getBusStopsOnRoad("R1")).extracting(BusStop::id).containsExactly("S1");
        assertThat(network.getBusStopsOnRoad("R2")).extracting(BusStop::id).containsExactly("S2");
        assertThat(network.getBusStopCount()).isEqualTo(2);
    }
}
