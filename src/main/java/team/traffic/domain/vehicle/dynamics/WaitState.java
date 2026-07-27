package team.traffic.domain.vehicle.dynamics;

import java.util.Optional;
import java.util.OptionalDouble;

/** Immutable wait episode, independent of the broad vehicle lifecycle status. */
public record WaitState(
        WaitCause cause,
        double sinceSeconds,
        Optional<WaitResource> resource,
        OptionalDouble expectedReleaseAtSeconds) {

    public WaitState {
        if (cause == null || !Double.isFinite(sinceSeconds) || sinceSeconds < 0) {
            throw new IllegalArgumentException("Wait cause and finite non-negative start time are required.");
        }
        resource = resource == null ? Optional.empty() : resource;
        expectedReleaseAtSeconds = expectedReleaseAtSeconds == null
                ? OptionalDouble.empty()
                : expectedReleaseAtSeconds;
        if (expectedReleaseAtSeconds.isPresent()
                && (!Double.isFinite(expectedReleaseAtSeconds.getAsDouble())
                        || expectedReleaseAtSeconds.getAsDouble() < sinceSeconds)) {
            throw new IllegalArgumentException("Expected release cannot precede the wait.");
        }
    }

    public static WaitState none(double observedAtSeconds) {
        return new WaitState(
                WaitCause.NONE,
                observedAtSeconds,
                Optional.empty(),
                OptionalDouble.empty());
    }

    public boolean isWaiting() {
        return cause != WaitCause.NONE;
    }

    public boolean isTimed() {
        return expectedReleaseAtSeconds.isPresent();
    }
}
