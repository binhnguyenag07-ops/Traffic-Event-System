package team.traffic.domain.demand;

import java.util.List;

public record FlowSpec(
        String id,
        PurposeId purpose,
        double share,
        List<VehicleMixEntry> vehicleMix,
        DemandCurve curve,
        CompletionPolicy completion,
        List<LegSpec> legs) {
    private static final double SUM_TOLERANCE = 1e-6;

    public FlowSpec {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Flow id is required");
        if (purpose == null) throw new IllegalArgumentException("Flow purpose is required");
        if (!Double.isFinite(share) || share <= 0 || share > 1) {
            throw new IllegalArgumentException("Flow share must be in (0, 1]");
        }
        if (vehicleMix == null || vehicleMix.isEmpty()) {
            throw new IllegalArgumentException("Flow vehicle mix is required");
        }
        vehicleMix = List.copyOf(vehicleMix);
        double mixSum = vehicleMix.stream().mapToDouble(VehicleMixEntry::share).sum();
        if (Math.abs(mixSum - 1.0) > SUM_TOLERANCE) {
            throw new IllegalArgumentException("Vehicle mix shares must sum to 1, got " + mixSum);
        }
        if (curve == null) throw new IllegalArgumentException("Flow demand curve is required");
        if (completion == null) throw new IllegalArgumentException("Flow completion policy is required");
        if (legs == null || legs.isEmpty()) throw new IllegalArgumentException("Flow requires at least one leg");
        legs = List.copyOf(legs);
    }
}
