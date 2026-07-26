package team.traffic.domain.demand;

import java.util.random.RandomGenerator;

/** Seed-resolved duration spent at one intermediate stop. */
public record DwellSpec(Kind kind, double seconds, double minSeconds, double maxSeconds) {
    public enum Kind { FIXED, RANGE }

    public DwellSpec {
        if (kind == null) throw new IllegalArgumentException("Dwell kind is required");
        if (!Double.isFinite(seconds) || !Double.isFinite(minSeconds) || !Double.isFinite(maxSeconds)) {
            throw new IllegalArgumentException("Dwell values must be finite");
        }
        if (kind == Kind.FIXED && seconds < 0) {
            throw new IllegalArgumentException("Fixed dwell seconds must not be negative");
        }
        if (kind == Kind.RANGE && (minSeconds < 0 || maxSeconds < minSeconds)) {
            throw new IllegalArgumentException("Dwell range must be non-negative and ordered");
        }
    }

    public static DwellSpec fixed(double seconds) {
        return new DwellSpec(Kind.FIXED, seconds, 0, 0);
    }

    public static DwellSpec range(double minSeconds, double maxSeconds) {
        return new DwellSpec(Kind.RANGE, 0, minSeconds, maxSeconds);
    }

    public double resolve(RandomGenerator random) {
        if (kind == Kind.FIXED || maxSeconds == minSeconds) return secondsOrMinimum();
        return minSeconds + random.nextDouble() * (maxSeconds - minSeconds);
    }

    private double secondsOrMinimum() {
        return kind == Kind.FIXED ? seconds : minSeconds;
    }
}
