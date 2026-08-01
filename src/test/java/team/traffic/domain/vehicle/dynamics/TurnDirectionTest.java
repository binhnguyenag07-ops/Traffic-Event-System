package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;


class TurnDirectionTest {

    private static final Intersection WEST = junction("W", -1, 0);
    private static final Intersection CENTRE = junction("C", 0, 0);
    private static final Intersection EAST = junction("E", 1, 0);
    private static final Intersection NORTH = junction("N", 0, 1);
    private static final Intersection SOUTH = junction("S", 0, -1);

    @Test
    void carryingOnInTheSameDirectionIsStraight() {
        assertThat(TurnDirection.of(WEST, CENTRE, EAST)).isEqualTo(TurnDirection.STRAIGHT);
    }

    @Test
    void turningAnticlockwiseIsALeftTurn() {
        assertThat(TurnDirection.of(WEST, CENTRE, NORTH)).isEqualTo(TurnDirection.LEFT);
    }

    @Test
    void turningClockwiseIsARightTurn() {
        assertThat(TurnDirection.of(WEST, CENTRE, SOUTH)).isEqualTo(TurnDirection.RIGHT);
    }

    @Test
    void doublingBackIsAUTurn() {
        assertThat(TurnDirection.of(WEST, CENTRE, WEST)).isEqualTo(TurnDirection.U_TURN);
    }

    @Test
    void onlyTurnsAcrossTheJunctionNeedAGap() {
        assertThat(TurnDirection.STRAIGHT.crossesOpposingTraffic()).isFalse();
        assertThat(TurnDirection.RIGHT.crossesOpposingTraffic()).isFalse();
        assertThat(TurnDirection.LEFT.crossesOpposingTraffic()).isTrue();
        assertThat(TurnDirection.U_TURN.crossesOpposingTraffic()).isTrue();
    }

    @Test
    void aVehicleStartingItsTripHasNoApproachAndCountsAsStraight() {
        assertThat(TurnDirection.of(null, CENTRE, NORTH)).isEqualTo(TurnDirection.STRAIGHT);
    }

    @Test
    void degenerateGeometryFallsBackToStraightInsteadOfStallingTheJunction() {
        assertThat(TurnDirection.of(CENTRE, CENTRE, NORTH)).isEqualTo(TurnDirection.STRAIGHT);
    }

    private static Intersection junction(String id, double x, double y) {
        return new Intersection(id, id, x, y);
    }
}
