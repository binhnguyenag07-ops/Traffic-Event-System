package team.traffic.simulation.trace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.vehicle.Car;

class VehicleTraceStoreTest {

    @Test
    void recordedPointsDoNotChangeWhenVehicleMutates() {
        VehicleTraceStore store = new VehicleTraceStore();
        Car vehicle = new Car("car-1", "A", "B");

        store.reset(0, List.of(vehicle));
        vehicle.setCurrentSpeed(20.0);
        vehicle.moveTo("C");
        store.record(1, List.of(vehicle));

        VehicleTracePoint initial = store.getVehicleStateAtTick("car-1", 0).orElseThrow();
        VehicleTracePoint changed = store.getVehicleStateAtTick("car-1", 1).orElseThrow();
        assertThat(initial.currentIntersectionId()).isEqualTo("A");
        assertThat(initial.currentSpeed()).isEqualTo(50.0);
        assertThat(changed.currentIntersectionId()).isEqualTo("C");
        assertThat(changed.currentSpeed()).isEqualTo(20.0);
        assertThatThrownBy(() -> initial.routeRoadIds().add("AB"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rangeQueriesAndSummaryAreOrderedAndResettable() {
        VehicleTraceStore store = new VehicleTraceStore();
        Car vehicle = new Car("car-1", "A", "B");

        store.reset(0, List.of(vehicle));
        store.record(1, List.of(vehicle));
        store.record(2, List.of(vehicle));

        assertThat(store.getVehicleTrace("car-1", 1, 2))
                .extracting(VehicleTracePoint::tick)
                .containsExactly(1L, 2L);
        assertThat(store.getSummary().firstTick()).isZero();
        assertThat(store.getSummary().lastTick()).isEqualTo(2L);
        assertThat(store.getSummary().recordedTickCount()).isEqualTo(3);
        assertThat(store.getSummary().vehicleIds()).containsExactly("car-1");

        store.reset(0, List.of(vehicle));

        assertThat(store.getSummary().recordedTickCount()).isEqualTo(1);
        assertThat(store.hasTick(1)).isFalse();
    }

    @Test
    void rejectsAnInvalidTickRange() {
        VehicleTraceStore store = new VehicleTraceStore();

        assertThatThrownBy(() -> store.getVehicleTrace("car-1", 4, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fromTick");
    }
}
