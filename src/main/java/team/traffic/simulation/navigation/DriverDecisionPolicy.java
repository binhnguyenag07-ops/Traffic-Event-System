package team.traffic.simulation.navigation;

import java.util.Optional;
import team.traffic.domain.routing.RerouteReason;
import team.traffic.domain.vehicle.dynamics.ExitReachability;
import team.traffic.domain.vehicle.dynamics.LaneChangeIntent;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.UTurnPolicy;

/** Deterministic pure strategic policy. */
public final class DriverDecisionPolicy {

    public DriverDecision decide(DriverContext context) {
        if (context == null) throw new IllegalArgumentException("Driver context is required.");
        double stalled = context.stall().stalledForSeconds(context.simulationSeconds());
        var decisionProfile = context.profile().decision();

        if (context.stall().cause() == WaitCause.RED_SIGNAL
                || context.stall().cause() == WaitCause.DWELL
                || context.stall().cause() == WaitCause.EVENT_STOPPED) {
            return new DriverDecision(
                    DriverDecisionKind.WAIT,
                    "The wait has an external timed release and does not escalate.",
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(context.stall().cause()));
        }

        if (context.local().exitReachability()
                == ExitReachability.REACHABLE_AFTER_SAFE_LANE_CHANGE) {
            return new DriverDecision(
                    DriverDecisionKind.REQUEST_LANE_CHANGE,
                    "A visible obstruction can be bypassed in one adjacent lane.",
                    Optional.empty(),
                    Optional.of(LaneChangeIntent.OBSTRUCTION_AVOIDANCE),
                    Optional.empty());
        }

        if (context.local().exitReachability()
                == ExitReachability.SEALED_BEFORE_NEXT_MANOEUVRE
                && stalled >= decisionProfile.stallEscapeSeconds()) {
            return new DriverDecision(
                    DriverDecisionKind.REPORT_STRANDED,
                    "The next legal manoeuvre point is physically unreachable.",
                    Optional.empty(), Optional.empty(),
                    Optional.of(WaitCause.LOCAL_OBSTRUCTION));
        }

        boolean knownHardBlock = context.knowledge()
                .activeAt(context.simulationSeconds()).stream()
                .anyMatch(advisory -> advisory.kind() == AdvisoryKind.CLOSED
                        || advisory.kind() == AdvisoryKind.LANES_BLOCKED);
        if (knownHardBlock
                && (context.navigation().hasUnseenKnowledge(
                                context.knowledge().getVersion())
                        || context.navigation().isDecisionDue(
                                context.simulationSeconds()))) {
            return reroute(RerouteReason.KNOWN_HARD_BLOCK, "A received notice reports a hard block.");
        }

        if (context.stall().cause() != WaitCause.NONE
                && stalled >= decisionProfile.stallEscapeSeconds()
                && decisionProfile.uTurnPolicy() != UTurnPolicy.NEVER
                && context.navigation().isDecisionDue(context.simulationSeconds())) {
            return new DriverDecision(
                    DriverDecisionKind.REQUEST_LEGAL_UTURN,
                    "The cause-local stall timeout permits a legal turnaround search.",
                    Optional.of(RerouteReason.STALL_TIMEOUT),
                    Optional.empty(), Optional.of(context.stall().cause()));
        }

        if (context.stall().cause() != WaitCause.NONE
                && stalled >= decisionProfile.reroutePatienceSeconds()
                && context.navigation().isDecisionDue(context.simulationSeconds())) {
            return reroute(RerouteReason.STALL_TIMEOUT, "Reroute patience was exceeded.");
        }

        boolean materialNotice = context.knowledge()
                .activeAt(context.simulationSeconds()).stream()
                .anyMatch(advisory ->
                        advisory.expectedDelaySeconds()
                                        >= decisionProfile.minimumExpectedSavingSeconds()
                                && context.simulationSeconds()
                                        - advisory.receivedAtSeconds()
                                        >= decisionProfile.reroutePatienceSeconds());
        if (materialNotice && context.navigation().isDecisionDue(context.simulationSeconds())) {
            return reroute(RerouteReason.EXPECTED_DELAY, "A received notice has material expected delay.");
        }
        if (context.stall().cause() != WaitCause.NONE) {
            return new DriverDecision(
                    DriverDecisionKind.WAIT,
                    "The current wait has not crossed its cause-aware escalation threshold.",
                    Optional.empty(), Optional.empty(), Optional.of(context.stall().cause()));
        }
        return DriverDecision.keepRoute();
    }

    private DriverDecision reroute(RerouteReason reason, String explanation) {
        return new DriverDecision(
                DriverDecisionKind.REQUEST_REROUTE,
                explanation,
                Optional.of(reason),
                Optional.empty(),
                Optional.empty());
    }
}
