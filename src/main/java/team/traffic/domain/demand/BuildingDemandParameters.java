package team.traffic.domain.demand;

/** Research-replaceable parameters for one building type. */
public record BuildingDemandParameters(
        double occupantsPerSquareMetre,
        double fallbackOccupants,
        double attractionWeight) {

    public BuildingDemandParameters {
        if (!Double.isFinite(occupantsPerSquareMetre) || occupantsPerSquareMetre < 0
                || !Double.isFinite(fallbackOccupants) || fallbackOccupants < 0
                || !Double.isFinite(attractionWeight) || attractionWeight < 0) {
            throw new IllegalArgumentException("Building demand parameters must be finite and non-negative");
        }
    }
}
