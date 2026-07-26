package team.traffic.domain.demand;

public record TripLeg(ResolvedPlace to, double dwellSeconds) {
    public TripLeg {
        if (to == null) throw new IllegalArgumentException("Trip leg destination is required");
        if (!Double.isFinite(dwellSeconds) || dwellSeconds < 0) {
            throw new IllegalArgumentException("Trip leg dwell seconds must be finite and non-negative");
        }
    }
}
