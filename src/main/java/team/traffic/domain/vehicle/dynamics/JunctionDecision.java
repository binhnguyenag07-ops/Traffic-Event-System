package team.traffic.domain.vehicle.dynamics;

import java.util.OptionalDouble;

/** Explicit grant or denial for one submitted junction approach. */
public record JunctionDecision(
        JunctionApproach approach,
        boolean granted,
        JunctionDenialReason denialReason,
        OptionalDouble expectedReleaseAtSeconds) {

    public JunctionDecision {
        if (approach == null || denialReason == null) {
            throw new IllegalArgumentException("Approach and denial reason are required.");
        }
        expectedReleaseAtSeconds = expectedReleaseAtSeconds == null
                ? OptionalDouble.empty()
                : expectedReleaseAtSeconds;
        if (granted && denialReason != JunctionDenialReason.NONE) {
            throw new IllegalArgumentException("A granted movement cannot have a denial reason.");
        }
        if (!granted && denialReason == JunctionDenialReason.NONE) {
            throw new IllegalArgumentException("A denied movement must explain why it was denied.");
        }
    }

    public static JunctionDecision granted(JunctionApproach approach) {
        return new JunctionDecision(
                approach, true, JunctionDenialReason.NONE, OptionalDouble.empty());
    }

    public static JunctionDecision denied(
            JunctionApproach approach,
            JunctionDenialReason reason) {
        return new JunctionDecision(approach, false, reason, OptionalDouble.empty());
    }

    public static JunctionDecision denied(
            JunctionApproach approach,
            JunctionDenialReason reason,
            OptionalDouble expectedReleaseAtSeconds) {
        return new JunctionDecision(
                approach, false, reason, expectedReleaseAtSeconds);
    }
}
