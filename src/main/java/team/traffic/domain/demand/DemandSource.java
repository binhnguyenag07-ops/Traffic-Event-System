package team.traffic.domain.demand;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record DemandSource(
        String id,
        PlaceSpec from,
        ProductionSpec production,
        List<FlowSpec> flows) {
    private static final double SUM_TOLERANCE = 1e-6;

    public DemandSource {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Demand source id is required");
        if (from == null || from.kind() == PlaceKind.ORIGIN) {
            throw new IllegalArgumentException("Demand source requires a non-origin place");
        }
        if (production == null) throw new IllegalArgumentException("Demand source production is required");
        if (production.kind() == ProductionSpec.Kind.POPULATION_DERIVED
                && from.kind() != PlaceKind.BUILDING && from.kind() != PlaceKind.ZONE) {
            throw new IllegalArgumentException(
                    "Population-derived production requires a BUILDING or ZONE source");
        }
        if (flows == null || flows.isEmpty()) throw new IllegalArgumentException("Demand source flows are required");
        flows = List.copyOf(flows);
        double shareSum = flows.stream().mapToDouble(FlowSpec::share).sum();
        if (shareSum > 1.0 + SUM_TOLERANCE) {
            throw new IllegalArgumentException("Flow shares within source must not exceed 1, got " + shareSum);
        }
        Set<String> ids = new HashSet<>();
        for (FlowSpec flow : flows) {
            if (!ids.add(flow.id())) throw new IllegalArgumentException("Duplicate flow id in source: " + flow.id());
        }
    }
}
