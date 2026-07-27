package team.traffic.domain.event;

import java.util.OptionalDouble;

/** Publication rule, deliberately separate from physical event activation. */
public record InformationPolicy(Kind kind, OptionalDouble publicationSeconds) {
    public enum Kind {
        LOCAL_ONLY,
        BROADCAST_ON_ACTIVATION,
        ANNOUNCED_AT
    }

    public InformationPolicy {
        if (kind == null) throw new IllegalArgumentException("Information-policy kind is required.");
        publicationSeconds = publicationSeconds == null
                ? OptionalDouble.empty()
                : publicationSeconds;
        if (kind == Kind.ANNOUNCED_AT && publicationSeconds.isEmpty()) {
            throw new IllegalArgumentException("ANNOUNCED_AT requires a publication time.");
        }
        if (publicationSeconds.isPresent()
                && (!Double.isFinite(publicationSeconds.getAsDouble())
                        || publicationSeconds.getAsDouble() < 0)) {
            throw new IllegalArgumentException("Publication time must be finite and non-negative.");
        }
    }

    public static InformationPolicy localOnly() {
        return new InformationPolicy(Kind.LOCAL_ONLY, OptionalDouble.empty());
    }

    public static InformationPolicy broadcastOnActivation() {
        return new InformationPolicy(Kind.BROADCAST_ON_ACTIVATION, OptionalDouble.empty());
    }

    public static InformationPolicy announcedAt(double simulationSeconds) {
        return new InformationPolicy(Kind.ANNOUNCED_AT, OptionalDouble.of(simulationSeconds));
    }

    public double publicationTimeFor(TrafficEvent event) {
        return switch (kind) {
            case LOCAL_ONLY, BROADCAST_ON_ACTIVATION -> event.getStartTimeSeconds();
            case ANNOUNCED_AT -> publicationSeconds.getAsDouble();
        };
    }
}
