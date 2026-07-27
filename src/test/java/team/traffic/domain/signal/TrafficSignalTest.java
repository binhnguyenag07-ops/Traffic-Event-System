package team.traffic.domain.signal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrafficSignalTest {
    @Test
    void cycleRepeatsAtExactPhaseBoundaries() {
        TrafficSignal signal = new TrafficSignal("J", List.of(
                new SignalPhase(Set.of("north"), 10),
                new SignalPhase(Set.of("east"), 5)));

        assertThat(signal.isGreenFor("north", 0)).isTrue();
        assertThat(signal.isGreenFor("east", 10)).isTrue();
        assertThat(signal.isGreenFor("north", 15)).isTrue();
        assertThat(signal.isGreenFor("east", 15)).isFalse();
    }

    @Test
    void expectedReleaseSkipsIntermediatePhasesThatStayRed() {
        TrafficSignal signal = new TrafficSignal("J", List.of(
                new SignalPhase(Set.of("north"), 10),
                new SignalPhase(Set.of("west"), 5),
                new SignalPhase(Set.of("east"), 7)));

        assertThat(signal.secondsUntilGreenFor("east", 2))
                .isEqualTo(13.0);
        assertThat(signal.secondsUntilGreenFor("north", 2))
                .isZero();
    }
}
