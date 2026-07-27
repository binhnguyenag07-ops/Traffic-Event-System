package team.traffic.domain.vehicle.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DriverDecisionProfileTest {

    @Test
    void seededVariationIsStableBoundedAndOrderIndependent() {
        DriverDecisionProfile base = DriverDecisionProfile.defaults();

        DriverDecisionProfile first = base.variedFor("car-17", 42L);
        DriverDecisionProfile replay = base.variedFor("car-17", 42L);
        DriverDecisionProfile otherSeed = base.variedFor("car-17", 43L);

        assertThat(replay).isEqualTo(first);
        assertThat(otherSeed).isNotEqualTo(first);
        assertThat(first.navigationHorizonSeconds())
                .isBetween(
                        base.navigationHorizonSeconds() * 0.85,
                        base.navigationHorizonSeconds() * 1.15);
        assertThat(first.noticeReceptionProbability()).isBetween(0.0, 1.0);
        assertThat(first.stallEscapeSeconds())
                .isGreaterThanOrEqualTo(first.reroutePatienceSeconds());
    }
}
