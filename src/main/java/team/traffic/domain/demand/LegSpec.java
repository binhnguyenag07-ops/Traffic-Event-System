package team.traffic.domain.demand;

public record LegSpec(PlaceSpec to, DwellSpec dwell) {
    public LegSpec {
        if (to == null) throw new IllegalArgumentException("Leg destination is required");
        dwell = dwell == null ? DwellSpec.fixed(0) : dwell;
    }
}
