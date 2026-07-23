package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class LateralExtentTest {

    @Test
    void edgesSitHalfAWidthEitherSideOfTheCentre() {
        LateralExtent car = new LateralExtent(-1.75, 1.8);

        assertThat(car.lowerEdgeMetres()).isCloseTo(-2.65, within(1e-12));
        assertThat(car.upperEdgeMetres()).isCloseTo(-0.85, within(1e-12));
    }

    @Test
    void twoCarsOnAdjacentLaneCentresLeaveTheGapAMotorcycleUses() {
        LateralExtent left = new LateralExtent(-1.75, 1.8);
        LateralExtent right = new LateralExtent(1.75, 1.8);

        assertThat(left.clearGapTo(right))
                .as("3.5 m between centres less 1.8 m of car")
                .isCloseTo(1.7, within(1e-12));
    }

    @Test
    void aMotorcycleFitsThroughThatGapAndACarDoesNot() {
        LateralExtent left = new LateralExtent(-1.75, 1.8);
        LateralExtent right = new LateralExtent(1.75, 1.8);
        double clearance = 0.30;

        assertThat(LateralExtent.fitsBetween(0.0, 0.75, left, clearance)).isTrue();
        assertThat(LateralExtent.fitsBetween(0.0, 0.75, right, clearance)).isTrue();
        assertThat(LateralExtent.fitsBetween(0.0, 1.8, left, clearance)).isFalse();
    }

    @Test
    void overlappingBodiesReportANegativeGap() {
        LateralExtent one = new LateralExtent(0.0, 1.8);
        LateralExtent other = new LateralExtent(0.5, 1.8);

        assertThat(one.clearGapTo(other)).isCloseTo(-1.3, within(1e-12));
        assertThat(one.overlaps(other, 0.0)).isTrue();
    }

    @Test
    void touchingExactlyIsNotOverlappingWithoutClearance() {
        LateralExtent one = new LateralExtent(0.0, 2.0);
        LateralExtent other = new LateralExtent(2.0, 2.0);

        assertThat(one.clearGapTo(other)).isCloseTo(0.0, within(1e-12));
        assertThat(one.overlaps(other, 0.0)).isFalse();
        assertThat(one.overlaps(other, 0.1))
                .as("a driver insisting on 0.1 m considers them too close")
                .isTrue();
    }

    @Test
    void theGapIsSymmetric() {
        LateralExtent one = new LateralExtent(-1.2, 0.75);
        LateralExtent other = new LateralExtent(2.4, 2.5);

        assertThat(one.clearGapTo(other)).isCloseTo(other.clearGapTo(one), within(1e-12));
    }

    @Test
    void rejectsNonPositiveWidthAndNonFiniteCentre() {
        assertThatThrownBy(() -> new LateralExtent(0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Width");
        assertThatThrownBy(() -> new LateralExtent(Double.NaN, 1.8))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite");
    }

    @Test
    void rejectsNegativeClearanceAndAMissingNeighbour() {
        LateralExtent one = new LateralExtent(0.0, 1.8);

        assertThatThrownBy(() -> one.overlaps(new LateralExtent(2.0, 1.8), -0.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Clearance");
        assertThatThrownBy(() -> one.clearGapTo(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
