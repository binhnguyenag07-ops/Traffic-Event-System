package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The signal-delay state machine, tested without a vehicle around it. */
class TrafficLightWaitTest {

    private final TrafficLightWait wait = new TrafficLightWait();

    @Test
    void aSignalHoldsForItsFullDelayThenLetsGo() {
        assertThat(wait.hold("ev-1", 3)).isTrue();
        assertThat(wait.getTicksRemaining()).isEqualTo(2);
        assertThat(wait.hold("ev-1", 3)).isTrue();
        assertThat(wait.hold("ev-1", 3)).isTrue();

        assertThat(wait.getTicksRemaining()).isZero();
        assertThat(wait.hold("ev-1", 3)).isFalse();
    }

    @Test
    void aSignalDelaysTheSameVehicleOnlyOnce() {
        wait.hold("ev-1", 1);

        assertThat(wait.hasServed("ev-1")).isTrue();
        assertThat(wait.hold("ev-1", 5)).isFalse();
    }

    @Test
    void adifferentSignalStartsItsOwnCountdown() {
        wait.hold("ev-1", 1);

        assertThat(wait.hold("ev-2", 2)).isTrue();
        assertThat(wait.getTicksRemaining()).isEqualTo(1);
    }

    @Test
    void clearingReleasesTheVehicleWithoutMarkingTheSignalServed() {
        wait.hold("ev-1", 5);

        wait.clear();

        assertThat(wait.getTicksRemaining()).isZero();
        assertThat(wait.hasServed("ev-1")).isFalse();
        assertThat(wait.hold("ev-1", 5)).isTrue();
    }

    @Test
    void anAbsentOrZeroDelaySignalNeverHolds() {
        assertThat(wait.hold(null, 5)).isFalse();
        assertThat(wait.hold("  ", 5)).isFalse();
        assertThat(wait.hold("ev-1", 0)).isFalse();
        assertThat(wait.hold("ev-1", -3)).isFalse();
    }
}
