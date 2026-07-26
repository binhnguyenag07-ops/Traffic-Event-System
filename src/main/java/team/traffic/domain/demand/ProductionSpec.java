package team.traffic.domain.demand;

public record ProductionSpec(Kind kind, double tripsPerPersonPerDay, double vehiclesPerDay) {
    public enum Kind { POPULATION_DERIVED, ABSOLUTE }

    public ProductionSpec {
        if (kind == null) throw new IllegalArgumentException("Production kind is required");
        if (!Double.isFinite(tripsPerPersonPerDay) || !Double.isFinite(vehiclesPerDay)) {
            throw new IllegalArgumentException("Production values must be finite");
        }
        if (kind == Kind.POPULATION_DERIVED && tripsPerPersonPerDay <= 0) {
            throw new IllegalArgumentException("Population production requires positive tripsPerPersonPerDay");
        }
        if (kind == Kind.ABSOLUTE && vehiclesPerDay <= 0) {
            throw new IllegalArgumentException("Absolute production requires positive vehiclesPerDay");
        }
    }

    public static ProductionSpec population(double tripsPerPersonPerDay) {
        return new ProductionSpec(Kind.POPULATION_DERIVED, tripsPerPersonPerDay, 0);
    }

    public static ProductionSpec absolute(double vehiclesPerDay) {
        return new ProductionSpec(Kind.ABSOLUTE, 0, vehiclesPerDay);
    }
}
