package team.traffic.domain.network;

/** Functional street hierarchy used for map detail and sensible editor defaults. */
public enum RoadClass {
    PRIMARY(3, 3.5),
    SECONDARY(2, 3.25),
    TERTIARY(2, 3.25),
    UNCLASSIFIED(1, 3.0),
    RESIDENTIAL(1, 3.0),
    LIVING_STREET(1, 3.0);

    private final int defaultLaneCount;
    private final double defaultLaneWidthMetres;

    RoadClass(int defaultLaneCount, double defaultLaneWidthMetres) {
        this.defaultLaneCount = defaultLaneCount;
        this.defaultLaneWidthMetres = defaultLaneWidthMetres;
    }

    public int getDefaultLaneCount() {
        return defaultLaneCount;
    }

    public double getDefaultLaneWidthMetres() {
        return defaultLaneWidthMetres;
    }
}
