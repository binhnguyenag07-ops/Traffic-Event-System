package team.traffic.domain.vehicle.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import team.traffic.domain.vehicle.dynamics.ExitReachability;
import team.traffic.domain.vehicle.dynamics.MotionOutcome;
import team.traffic.domain.vehicle.dynamics.MovementPlan;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitResource;
import team.traffic.domain.vehicle.dynamics.WaitState;

class ProgressStallTrackerTest {

    @Test
    void subThresholdMotionAccumulatesUntilItProvesMeaningfulProgress() {
        ProgressStallTracker tracker =
                new ProgressStallTracker(1.0, new double[] {5.0, 15.0});

        tracker.update(outcome(0.0, 0.4), ExitReachability.REACHABLE);
        tracker.update(outcome(1.0, 0.4), ExitReachability.REACHABLE);

        assertThat(tracker.snapshot().cause()).isEqualTo(WaitCause.LEADER);
        assertThat(tracker.snapshot().lastProgressAtSeconds()).isZero();

        tracker.update(outcome(2.0, 0.2), ExitReachability.REACHABLE);

        assertThat(tracker.snapshot().cause()).isEqualTo(WaitCause.NONE);
        assertThat(tracker.snapshot().lastProgressAtSeconds()).isEqualTo(3.0);
    }

    @Test
    void failedRouteAttemptsUseConfiguredBackoffBoundaries() {
        ProgressStallTracker tracker =
                new ProgressStallTracker(1.0, new double[] {5.0, 15.0});

        tracker.recordRouteAttempt(10.0, "A", false);
        assertThat(tracker.retryDue(14.999)).isFalse();
        assertThat(tracker.retryDue(15.0)).isTrue();

        tracker.recordRouteAttempt(15.0, "B", false);
        assertThat(tracker.retryDue(29.999)).isFalse();
        assertThat(tracker.retryDue(30.0)).isTrue();

        tracker.recordRouteAttempt(30.0, "C", false);
        assertThat(tracker.snapshot().nextRetryAtSeconds()).isEqualTo(45.0);
    }

    private static MotionOutcome outcome(double observedAt, double progressMetres) {
        return new MotionOutcome(
                Optional.of(new MovementPlan(
                        "AB", 0.05, progressMetres, false)),
                new WaitState(
                        WaitCause.LEADER,
                        observedAt,
                        Optional.of(new WaitResource.Leader("ahead")),
                        java.util.OptionalDouble.empty()),
                observedAt,
                1.0);
    }
}
