package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RoadConditionTest {

    @Test
    void calculatesTrafficCostMultiplierFromNormalizedInputs() {
        RoadCondition condition = new RoadCondition(0.25, 0.5, RoadStatus.CONGESTED);

        assertThat(condition.getTravelCostMultiplier()).isEqualTo(1.75);
    }

    @Test
    void returnsInfiniteMultiplierForUnroutableStatus() {
        RoadCondition blocked = new RoadCondition(0.5, 0.5, RoadStatus.BLOCKED);
        RoadCondition closed = new RoadCondition(0.5, 0.5, RoadStatus.CLOSED);

        assertThat(blocked.getTravelCostMultiplier()).isEqualTo(Double.POSITIVE_INFINITY);
        assertThat(closed.getTravelCostMultiplier()).isEqualTo(Double.POSITIVE_INFINITY);
    }

    @Test
    void clampsFiniteRatiosToSupportedRange() {
        RoadCondition condition = new RoadCondition(-0.5, 1.5, RoadStatus.OPEN);

        assertThat(condition.getCongestionLevel()).isZero();
        assertThat(condition.getVehicleDensity()).isEqualTo(1.0);
    }

    @Test
    void rejectsNonFiniteTrafficRatios() {
        assertThatThrownBy(() -> new RoadCondition(Double.NaN, 0, RoadStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Congestion level must be finite");

        assertThatThrownBy(() -> new RoadCondition(0, Double.POSITIVE_INFINITY, RoadStatus.OPEN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vehicle density must be finite");

        RoadCondition condition = new RoadCondition(0, 0, RoadStatus.OPEN);
        assertThatThrownBy(() -> condition.setCongestionLevel(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Congestion level must be finite");
        assertThatThrownBy(() -> condition.setVehicleDensity(Double.NEGATIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Vehicle density must be finite");
    }

    @Test
    void rejectsNonFiniteCongestionIncrease() {
        RoadCondition condition = new RoadCondition(0, 0, RoadStatus.OPEN);

        assertThatThrownBy(() -> condition.increaseCongestion(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Congestion increase must be finite and non-negative");
        assertThatThrownBy(() -> condition.increaseCongestion(Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Congestion increase must be finite and non-negative");
    }

    @Test
    void openRoadBecomesCongestedWhenCongestionIncreases() {
        RoadCondition condition = new RoadCondition(0, 0, RoadStatus.OPEN);

        double updatedCongestion = condition.increaseCongestion(0.4);

        assertThat(updatedCongestion).isEqualTo(0.4);
        assertThat(condition.getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        assertThat(condition.isRoutable()).isTrue();
    }

    @Test
    void congestedRoadReturnsToOpenWhenCongestionIsCleared() {
        RoadCondition condition = new RoadCondition(0.6, 0.2, RoadStatus.CONGESTED);

        condition.setCongestionLevel(0);

        assertThat(condition.getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        assertThat(condition.getTravelCostMultiplier()).isEqualTo(1.2);
    }

    @Test
    void blockedRoadIsNotRoutableUntilOpenedAgain() {
        RoadCondition condition = new RoadCondition(0.2, 0.3, RoadStatus.OPEN);

        condition.markBlocked();

        assertThat(condition.getRoadStatus()).isEqualTo(RoadStatus.BLOCKED);
        assertThat(condition.getCongestionLevel()).isEqualTo(1.0);
        assertThat(condition.isRoutable()).isFalse();
        assertThat(condition.getTravelCostMultiplier()).isEqualTo(Double.POSITIVE_INFINITY);

        condition.markOpen();

        assertThat(condition.getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        assertThat(condition.getCongestionLevel()).isZero();
        assertThat(condition.isRoutable()).isTrue();
    }
}
