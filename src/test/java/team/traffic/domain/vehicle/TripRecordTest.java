package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/** Journey accumulation, tested without a vehicle around it. */
class TripRecordTest {

    private final TripRecord trip = new TripRecord("A");

    @Test
    void aTripStartsWhereTheVehicleDoes() {
        assertThat(trip.getVisitedIntersections()).containsExactly("A");
        assertThat(trip.getDistanceTravelledKm()).isZero();
        assertThat(trip.getTravelTimeSeconds()).isZero();
        assertThat(trip.getWaitingTicks()).isZero();
        assertThat(trip.getRouteRecalculationCount()).isZero();
    }

    @Test
    void everyJunctionReachedIsRecordedInOrder() {
        trip.recordArrivalAt("B");
        trip.recordArrivalAt("C");

        assertThat(trip.getVisitedIntersections()).containsExactly("A", "B", "C");
    }

    @Test
    void distanceTimeWaitingAndReroutesAccumulate() {
        trip.addDistanceKm(1.5);
        trip.addDistanceKm(0.25);
        trip.addTravelTime(30);
        trip.addTravelTime(10);
        trip.recordWaitingTick();
        trip.recordWaitingTick();
        trip.recordRouteRecalculation();

        assertThat(trip.getDistanceTravelledKm()).isCloseTo(1.75, within(1e-9));
        assertThat(trip.getTravelTimeSeconds()).isCloseTo(40.0, within(1e-9));
        assertThat(trip.getWaitingTicks()).isEqualTo(2);
        assertThat(trip.getRouteRecalculationCount()).isEqualTo(1);
    }

    @Test
    void theVisitedListCannotBeChangedFromOutside() {
        assertThatThrownBy(() -> trip.getVisitedIntersections().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nonsensicalValuesAreRejectedRatherThanAccumulated() {
        assertThatThrownBy(() -> trip.addDistanceKm(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trip.addTravelTime(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trip.addDistanceKm(Double.NaN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> trip.recordArrivalAt(" ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TripRecord(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
