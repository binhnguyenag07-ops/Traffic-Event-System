package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * These limits are what stop the model reporting throughput gains earned by
 * riders behaving impossibly, so they are worth pinning precisely.
 */
class FilteringRulesTest {
    private final FilteringRules rules = FilteringRules.defaults();

    @Test
    void aRiderInALaneIsLimitedOnlyByCarFollowing() {
        assertThat(rules.cappedSpeedMps(20.0, 0.0, false))
                .as("not filtering, so no cap applies")
                .isEqualTo(20.0);
    }

    @Test
    void aRiderThreadingStoppedTrafficIsHeldToTheDifferential() {
        double capped = rules.cappedSpeedMps(20.0, 0.0, true);

        assertThat(capped)
                .as("stopped traffic plus the 6 m/s differential")
                .isCloseTo(6.0, within(1e-12));
    }

    @Test
    void theAbsoluteCeilingBindsWhenTrafficIsAlreadyMoving() {
        double capped = rules.cappedSpeedMps(30.0, 25.0, true);

        assertThat(capped)
                .as("25 + 6 exceeds the ceiling, so the ceiling wins")
                .isCloseTo(14.0, within(1e-12));
    }

    @Test
    void aSlowRiderIsNotSpedUpByTheCap() {
        assertThat(rules.cappedSpeedMps(3.0, 20.0, true)).isEqualTo(3.0);
    }

    @Test
    void defaultsMatchTheDocumentedFilteringGuidance() {
        assertThat(rules.lateralClearanceMetres()).isEqualTo(0.30);
        assertThat(rules.maxSpeedDifferentialMps()).isEqualTo(6.0);
        assertThat(rules.maxFilteringSpeedMps()).isEqualTo(14.0);
    }

    @Test
    void rejectsNonPositiveLimits() {
        assertThatThrownBy(() -> new FilteringRules(0.0, 6.0, 14.0, 15.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lateral clearance");
        assertThatThrownBy(() -> new FilteringRules(0.3, 0.0, 14.0, 15.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Speed differential");
        assertThatThrownBy(() -> new FilteringRules(0.3, 6.0, -1.0, 15.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Maximum filtering speed");
        assertThatThrownBy(() -> new FilteringRules(0.3, 6.0, 14.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Neighbour window");
    }
}
