package team.traffic.domain.vehicle.dynamics;

/**
 * The strip of road a participant occupies across its width.
 *
 * <p>Whether two vehicles are in each other's way used to be answered by
 * comparing lane indexes. That works only while every vehicle sits on a lane
 * centre. A motorcycle riding the line between two lanes has no index that
 * describes it, and a car it has ample room to pass shares the index it is
 * nominally in — so the model would stop it dead behind traffic it could
 * physically ride around.</p>
 *
 * <p>Comparing extents answers the same question in metres and stays correct in
 * both cases. Two cars on adjacent lane centres of a 3.5 m road leave 1.7 m
 * clear between their mirrors, which a 0.75 m motorcycle fits through with
 * clearance to spare; the same arithmetic says a second car does not.</p>
 *
 * @param centreMetres signed offset of the centreline of the participant from
 *                     the road centreline
 * @param widthMetres  full width, mirror to mirror
 */
public record LateralExtent(double centreMetres, double widthMetres) {

    public LateralExtent {
        if (!Double.isFinite(centreMetres)) {
            throw new IllegalArgumentException("Lateral centre must be finite.");
        }
        if (!Double.isFinite(widthMetres) || widthMetres <= 0) {
            throw new IllegalArgumentException("Width must be positive.");
        }
    }

    public double lowerEdgeMetres() {
        return centreMetres - widthMetres / 2.0;
    }

    public double upperEdgeMetres() {
        return centreMetres + widthMetres / 2.0;
    }

    /**
     * Clear distance between the two extents, negative when they overlap.
     *
     * <p>Measured edge to edge, so it is the room actually left between two
     * vehicles rather than the distance between their centrelines.</p>
     */
    public double clearGapTo(LateralExtent other) {
        requireOther(other);
        return Math.abs(centreMetres - other.centreMetres)
                - (widthMetres + other.widthMetres) / 2.0;
    }

    /**
     * Whether the two participants are in each other's way, allowing for the
     * clearance a driver insists on keeping beside them.
     */
    public boolean overlaps(LateralExtent other, double clearanceMetres) {
        requireOther(other);
        if (!Double.isFinite(clearanceMetres) || clearanceMetres < 0) {
            throw new IllegalArgumentException("Clearance must not be negative.");
        }
        return clearGapTo(other) < clearanceMetres;
    }

    /** Whether a participant of this width fits centred on {@code centre}. */
    public static boolean fitsBetween(
            double centre, double widthMetres, LateralExtent neighbour, double clearanceMetres) {
        return !new LateralExtent(centre, widthMetres).overlaps(neighbour, clearanceMetres);
    }

    private void requireOther(LateralExtent other) {
        if (other == null) {
            throw new IllegalArgumentException("Other extent is required.");
        }
    }
}
