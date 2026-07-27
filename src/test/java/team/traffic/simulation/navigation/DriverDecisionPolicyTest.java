package team.traffic.simulation.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;
import team.traffic.domain.routing.RouteCost;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.DriverProfile;
import team.traffic.domain.vehicle.dynamics.ExitReachability;
import team.traffic.domain.vehicle.dynamics.LocalPerception;
import team.traffic.domain.vehicle.dynamics.VehicleLocation;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitResource;
import team.traffic.domain.vehicle.navigation.DriverKnowledge;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.InformationSource;
import team.traffic.domain.vehicle.navigation.NavigationState;
import team.traffic.domain.vehicle.navigation.RoadAdvisory;
import team.traffic.domain.vehicle.navigation.StallState;

class DriverDecisionPolicyTest {
    private final DriverDecisionPolicy policy = new DriverDecisionPolicy();

    @Test
    void timedExternalWaitNeverEscalatesIntoRerouting() {
        DriverDecision decision = policy.decide(context(
                WaitCause.RED_SIGNAL,
                ExitReachability.WAITING_FOR_TEMPORARY_SPACE,
                200.0,
                new NavigationState()));

        assertThat(decision.kind()).isEqualTo(DriverDecisionKind.WAIT);
    }

    @Test
    void sealedRoadReportsStrandedWithoutInventingAnEscape() {
        DriverDecision decision = policy.decide(context(
                WaitCause.LOCAL_OBSTRUCTION,
                ExitReachability.SEALED_BEFORE_NEXT_MANOEUVRE,
                200.0,
                new NavigationState()));

        assertThat(decision.kind())
                .isEqualTo(DriverDecisionKind.REPORT_STRANDED);
    }

    @Test
    void cooldownSuppressesAnOtherwiseDueStallReroute() {
        NavigationState navigation = new NavigationState();
        navigation.recordRejected("STALL_TIMEOUT", "route", 250.0);

        DriverDecision decision = policy.decide(context(
                WaitCause.LEADER,
                ExitReachability.WAITING_FOR_TEMPORARY_SPACE,
                200.0,
                navigation));

        assertThat(decision.kind()).isEqualTo(DriverDecisionKind.WAIT);
    }

    @Test
    void ordinaryDelayWaitsForPatienceAfterNoticeReception() {
        DriverKnowledge knowledge = new DriverKnowledge();
        knowledge.receive(advisory(AdvisoryKind.CONGESTED, 100.0));

        DriverDecision early = policy.decide(context(
                WaitCause.NONE,
                ExitReachability.REACHABLE,
                124.999,
                new NavigationState(),
                knowledge));
        DriverDecision due = policy.decide(context(
                WaitCause.NONE,
                ExitReachability.REACHABLE,
                125.0,
                new NavigationState(),
                knowledge));

        assertThat(early.kind()).isEqualTo(DriverDecisionKind.KEEP_ROUTE);
        assertThat(due.kind()).isEqualTo(DriverDecisionKind.REQUEST_REROUTE);
    }

    @Test
    void newlyReceivedHardBlockBypassesExistingCooldownOnce() {
        NavigationState navigation = new NavigationState();
        navigation.recordRejected("EXPECTED_DELAY", "route", 250.0);
        DriverKnowledge knowledge = new DriverKnowledge();
        knowledge.receive(advisory(AdvisoryKind.CLOSED, 200.0));

        DriverDecision decision = policy.decide(context(
                WaitCause.NONE,
                ExitReachability.REACHABLE,
                200.0,
                navigation,
                knowledge));

        assertThat(decision.kind()).isEqualTo(DriverDecisionKind.REQUEST_REROUTE);
        assertThat(decision.rerouteReason()).contains(
                team.traffic.domain.routing.RerouteReason.KNOWN_HARD_BLOCK);
    }

    private static DriverContext context(
            WaitCause cause,
            ExitReachability reachability,
            double now,
            NavigationState navigation) {
        return context(
                cause,
                reachability,
                now,
                navigation,
                new DriverKnowledge());
    }

    private static DriverContext context(
            WaitCause cause,
            ExitReachability reachability,
            double now,
            NavigationState navigation,
            DriverKnowledge knowledge) {
        LocalPerception perception = new LocalPerception(
                now,
                50.0,
                new VehicleLocation(
                        Optional.of("AB"),
                        Optional.empty(),
                        0.5,
                        0,
                        0.0),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                reachability);
        StallState stall = new StallState(
                0.0,
                cause == WaitCause.NONE ? now : 0.0,
                cause,
                0.0,
                Optional.of(new WaitResource.Leader("ahead")),
                Double.NaN,
                null,
                0.0,
                reachability);
        return new DriverContext(
                perception,
                knowledge,
                navigation,
                stall,
                new RouteCost(100.0, 1.0, 0.0, 0.0),
                DriverProfile.forType(VehicleType.CAR),
                now);
    }

    private static RoadAdvisory advisory(
            AdvisoryKind kind,
            double receivedAtSeconds) {
        return new RoadAdvisory(
                "notice:" + kind,
                "AB",
                0,
                100.0,
                receivedAtSeconds + 20.0,
                kind,
                50.0,
                1.0,
                InformationSource.CONNECTED_NAVIGATION,
                receivedAtSeconds,
                receivedAtSeconds,
                OptionalDouble.empty());
    }
}
