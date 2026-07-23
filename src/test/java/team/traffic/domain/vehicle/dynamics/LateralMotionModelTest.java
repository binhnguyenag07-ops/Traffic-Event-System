package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class LateralMotionModelTest {

    @Test
    void movesTowardsTheTargetAtTheCappedSpeed() {
        double next = LateralMotionModel.nextOffsetMetres(-1.75, 1.75, 1.0, 0.5);

        assertThat(next).isCloseTo(-1.25, within(1e-12));
    }

    @Test
    void movesTowardsATargetOnTheOtherSide() {
        double next = LateralMotionModel.nextOffsetMetres(1.75, -1.75, 1.0, 0.5);

        assertThat(next).isCloseTo(1.25, within(1e-12));
    }

    @Test
    void arrivesExactlyRatherThanOvershooting() {
        double next = LateralMotionModel.nextOffsetMetres(1.6, 1.75, 1.0, 1.0);

        assertThat(next).isEqualTo(1.75);
    }

    @Test
    void staysPutOnceItHasArrived() {
        double next = LateralMotionModel.nextOffsetMetres(1.75, 1.75, 1.0, 1.0);

        assertThat(next).isEqualTo(1.75);
    }

    @Test
    void crossingAStandardLaneTakesSeveralSecondsAtTheDefaultSpeed() {
        double offset = -1.75;
        int steps = 0;
        while (!LateralMotionModel.hasSettled(offset, 1.75) && steps < 100) {
            offset = LateralMotionModel.nextOffsetMetres(offset, 1.75, 1.0, 0.5);
            steps++;
        }

        // 3.5 metres at 1 m/s in half-second steps: seven steps, three and a
        // half seconds. A lane change that used to be instant now has a
        // duration a driver would recognise.
        assertThat(steps).isEqualTo(7);
        assertThat(offset).isCloseTo(1.75, within(1e-12));
    }

    @Test
    void aMotorcycleCrossesALaneFasterThanABus() {
        double motorcycle = LateralMotionModel.crossingSecondsFor(3.5, 1.6);
        double bus = LateralMotionModel.crossingSecondsFor(3.5, 0.7);

        assertThat(motorcycle).isLessThan(bus);
    }

    @Test
    void hasSettledOnlyWithinTheArrivalTolerance() {
        assertThat(LateralMotionModel.hasSettled(1.75, 1.75)).isTrue();
        assertThat(LateralMotionModel.hasSettled(1.75, 1.75 + 1e-9)).isTrue();
        assertThat(LateralMotionModel.hasSettled(1.75, 1.80)).isFalse();
    }

    @Test
    void rejectsNonPositiveLateralSpeed() {
        assertThatThrownBy(() -> LateralMotionModel.nextOffsetMetres(0, 1, 0, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lateral speed");
    }

    @Test
    void rejectsNonPositiveTimeStep() {
        assertThatThrownBy(() -> LateralMotionModel.nextOffsetMetres(0, 1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Time step");
    }

    @Test
    void rejectsNonFiniteOffsets() {
        assertThatThrownBy(() -> LateralMotionModel.nextOffsetMetres(Double.NaN, 1, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> LateralMotionModel.nextOffsetMetres(0, Double.POSITIVE_INFINITY, 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
