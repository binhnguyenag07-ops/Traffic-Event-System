package team.traffic.domain.vehicle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * The point of gathering these values is that they are acquired and discarded
 * as one act, so that is what these tests hold: nothing survives a release, and
 * entering a road sets every one of them.
 */
class RoadPlacementTest {

    @Test
    void aFreshPlacementHoldsNoRoad() {
        RoadPlacement placement = new RoadPlacement();

        assertThat(placement.isOnRoad()).isFalse();
        assertThat(placement.getOccupiedRoadId()).isNull();
        assertThat(placement.getProgress()).isZero();
        assertThat(placement.getLaneIndex()).isZero();
        assertThat(placement.getLateralOffsetMetres()).isZero();
    }

    @Test
    void enteringARoadLinesTheVehicleUpWithItsLane() {
        RoadPlacement placement = new RoadPlacement();

        placement.enter("AB", "A", "B", 2, 3.5);

        assertThat(placement.isOnRoad()).isTrue();
        assertThat(placement.getOccupiedRoadId()).isEqualTo("AB");
        assertThat(placement.getStartIntersectionId()).isEqualTo("A");
        assertThat(placement.getEndIntersectionId()).isEqualTo("B");
        assertThat(placement.getProgress()).isZero();
        assertThat(placement.getLaneIndex()).isEqualTo(2);
        assertThat(placement.getLateralOffsetMetres()).isEqualTo(3.5);
        assertThat(placement.getLateralTargetOffsetMetres())
                .as("a vehicle joining a road is not already mid-manoeuvre")
                .isEqualTo(3.5);
    }

    @Test
    void releasingClearsEveryValueNotJustTheRoad() {
        RoadPlacement placement = new RoadPlacement();
        placement.enter("AB", "A", "B", 2, 3.5);
        placement.moveTo(0.8, 1, 0.4, -1.75);

        placement.release();

        // This is the invariant the extraction exists to guarantee: carrying any
        // of these onto the next road would start a vehicle part way along it,
        // or in a lane the new road may not have.
        assertThat(placement.isOnRoad()).isFalse();
        assertThat(placement.getOccupiedRoadId()).isNull();
        assertThat(placement.getStartIntersectionId()).isNull();
        assertThat(placement.getEndIntersectionId()).isNull();
        assertThat(placement.getProgress()).isZero();
        assertThat(placement.getLaneIndex()).isZero();
        assertThat(placement.getLateralOffsetMetres()).isZero();
        assertThat(placement.getLateralTargetOffsetMetres()).isZero();
    }

    @Test
    void enteringAgainReplacesThePreviousPositionEntirely() {
        RoadPlacement placement = new RoadPlacement();
        placement.enter("AB", "A", "B", 2, 3.5);
        placement.moveTo(0.9, 2, 3.5, 3.5);

        placement.enter("BC", "B", "C", 0, -1.75);

        assertThat(placement.getOccupiedRoadId()).isEqualTo("BC");
        assertThat(placement.getProgress())
                .as("progress along the old road means nothing on the new one")
                .isZero();
        assertThat(placement.getLaneIndex()).isZero();
        assertThat(placement.getLateralOffsetMetres()).isEqualTo(-1.75);
    }

    @Test
    void movingRecordsProgressAndLateralPositionTogether() {
        RoadPlacement placement = new RoadPlacement();
        placement.enter("AB", "A", "B", 0, -1.75);

        placement.moveTo(0.25, 1, -0.5, 1.75);

        assertThat(placement.getProgress()).isEqualTo(0.25);
        assertThat(placement.getLaneIndex()).isEqualTo(1);
        assertThat(placement.getLateralOffsetMetres()).isEqualTo(-0.5);
        assertThat(placement.getLateralTargetOffsetMetres())
                .as("still crossing towards the lane it committed to")
                .isEqualTo(1.75);
    }

    @Test
    void rejectsAProgressOutsideTheRoad() {
        RoadPlacement placement = new RoadPlacement();
        placement.enter("AB", "A", "B", 0, 0.0);

        assertThatThrownBy(() -> placement.moveTo(1.1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Road progress");
        assertThatThrownBy(() -> placement.moveTo(-0.1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankRoadAndANegativeLane() {
        RoadPlacement placement = new RoadPlacement();

        assertThatThrownBy(() -> placement.enter(" ", "A", "B", 0, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("road id");
        assertThatThrownBy(() -> placement.enter("AB", "A", "B", -1, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lane index");
    }

    @Test
    void rejectsANonFiniteLateralPosition() {
        RoadPlacement placement = new RoadPlacement();

        assertThatThrownBy(() -> placement.enter("AB", "A", "B", 0, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
        placement.enter("AB", "A", "B", 0, 0.0);
        assertThatThrownBy(() -> placement.moveTo(0.5, 0, Double.POSITIVE_INFINITY, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lateral position");
    }
}
