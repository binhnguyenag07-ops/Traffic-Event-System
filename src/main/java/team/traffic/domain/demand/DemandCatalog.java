package team.traffic.domain.demand;

import java.util.Map;

/** Parameter and curve tables loaded from data rather than compiled into Java. */
public record DemandCatalog(
        String status,
        String note,
        DemandParameters parameters,
        Map<String, DemandCurve> curves) {

    public DemandCatalog {
        if (status == null || status.isBlank()) throw new IllegalArgumentException("Catalog status is required");
        note = note == null ? "" : note;
        if (parameters == null) throw new IllegalArgumentException("Demand parameters are required");
        if (curves == null || curves.isEmpty()) throw new IllegalArgumentException("Demand curves are required");
        curves = Map.copyOf(curves);
    }

    public DemandCurve requireCurve(String id) {
        DemandCurve curve = curves.get(id);
        if (curve == null) throw new IllegalArgumentException("Unknown demand curve: " + id);
        return curve;
    }
}
