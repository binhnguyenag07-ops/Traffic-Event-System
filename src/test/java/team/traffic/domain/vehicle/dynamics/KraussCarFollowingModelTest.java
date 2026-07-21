package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import team.traffic.domain.vehicle.VehicleType;

class KraussCarFollowingModelTest {

    private static final DriverProfile CAR = DriverProfile.forType(VehicleType.CAR);
    private final KraussCarFollowingModel model = new KraussCarFollowingModel();

    @Test
    void acceleratesTowardsTheDesiredSpeedOnAnEmptyRoad() {
        double speed = model.nextSpeedMps(context(10.0, 20.0, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY));

        assertThat(speed).isGreaterThan(10.0).isLessThanOrEqualTo(20.0);
    }

    @Test
    void neverExceedsTheDesiredSpeed() {
        double speed = model.nextSpeedMps(context(20.0, 20.0, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY));

        assertThat(speed).isLessThanOrEqualTo(20.0);
    }

    @Test
    void slowsDownBehindASlowerLeader() {
        LeaderGap closeSlowLeader = new LeaderGap(8.0, 5.0);

        double speed = model.nextSpeedMps(context(20.0, 20.0, closeSlowLeader, Double.POSITIVE_INFINITY));

        assertThat(speed).isLessThan(20.0);
    }

    @Test
    void stopsWhenItIsAlreadyTouchingTheLeader() {
        LeaderGap noGap = new LeaderGap(0.0, 0.0);

        double speed = model.nextSpeedMps(context(15.0, 20.0, noGap, Double.POSITIVE_INFINITY));

        assertThat(speed).isZero();
    }

    @Test
    void neverTravelsFurtherThanTheGapAhead() {
        double timeStepSeconds = 2.0;
        LeaderGap gap = new LeaderGap(6.0, 0.0);

        double speed = model.nextSpeedMps(new FollowContext(
                20.0, 20.0, 2.5, 6.5, CAR, gap, Double.POSITIVE_INFINITY, timeStepSeconds, 0.0));

        assertThat(speed * timeStepSeconds).isLessThanOrEqualTo(6.0);
    }

    @Test
    void brakesForAStopPointEvenWithAnEmptyRoadAhead() {
        double freeRoadSpeed =
                model.nextSpeedMps(context(20.0, 20.0, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY));
        double blockedSpeed = model.nextSpeedMps(context(20.0, 20.0, LeaderGap.FREE_ROAD, 4.0));

        assertThat(blockedSpeed).isLessThan(freeRoadSpeed);
    }

    @Test
    void aCoarseTimeStepDoesNotLetImperfectionStallTheVehicle() {
        // The imperfection term is integrated over the reaction time, so a long
        // step must not subtract minutes' worth of acceleration.
        FollowContext coarseStep = new FollowContext(
                13.9, 13.9, 2.5, 6.5, CAR, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY, 180.0, 0.999);

        assertThat(model.nextSpeedMps(coarseStep)).isGreaterThan(10.0);
    }

    @Test
    void theSameInputAlwaysProducesTheSameSpeed() {
        FollowContext context = context(12.0, 20.0, new LeaderGap(30.0, 10.0), Double.POSITIVE_INFINITY);

        assertThat(model.nextSpeedMps(context)).isEqualTo(model.nextSpeedMps(context));
    }

    @Test
    void aPerfectDriverGivesNothingAwayButASloppyOneDoes() {
        DriverProfile perfect = new DriverProfile(2.5, 1.0, 0.0, 3.0, 1.0);
        DriverProfile sloppy = new DriverProfile(2.5, 1.0, 1.0, 3.0, 1.0);

        double perfectSpeed = model.nextSpeedMps(new FollowContext(
                15.0, 15.0, 2.5, 6.5, perfect, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY, 1.0, 0.8));
        double sloppySpeed = model.nextSpeedMps(new FollowContext(
                15.0, 15.0, 2.5, 6.5, sloppy, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY, 1.0, 0.8));

        assertThat(perfectSpeed).isEqualTo(15.0);
        assertThat(sloppySpeed).isLessThan(perfectSpeed);
    }

    @Test
    void safeSpeedIsUnboundedOnAnOpenRoad() {
        assertThat(KraussCarFollowingModel.safeSpeedMps(
                Double.POSITIVE_INFINITY, 0.0, 15.0, 6.5, 1.0)).isInfinite();
    }

    @Test
    void aNegativeGapIsTreatedAsNoGap() {
        assertThat(new LeaderGap(-5.0, 3.0).gapMetres()).isZero();
    }

    @Test
    void rejectsNoiseOutsideTheUnitInterval() {
        assertThatThrownBy(() -> context(10.0, 20.0, LeaderGap.FREE_ROAD, Double.POSITIVE_INFINITY, 1.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Noise");
    }

    private static FollowContext context(
            double currentSpeedMps, double desiredSpeedMps, LeaderGap gap, double stopDistanceMetres) {
        return context(currentSpeedMps, desiredSpeedMps, gap, stopDistanceMetres, 0.0);
    }

    private static FollowContext context(
            double currentSpeedMps,
            double desiredSpeedMps,
            LeaderGap gap,
            double stopDistanceMetres,
            double noise) {
        return new FollowContext(
                currentSpeedMps, desiredSpeedMps, 2.5, 6.5, CAR, gap, stopDistanceMetres, 1.0, noise);
    }
}
