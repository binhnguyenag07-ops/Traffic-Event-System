package team.traffic.domain.vehicle.dynamics;

/**
 * The limits that keep filtering honest.
 *
 * <p>A model that lets a motorcycle ride any gap it geometrically fits through
 * will report throughput gains that come from riders behaving impossibly:
 * threading a queue of stopped traffic at road speed. The gains would be real
 * numbers produced by an unreal manoeuvre, which is worse than not modelling it
 * at all.</p>
 *
 * <p>Three of these limits are the ones filtering guidance actually uses.
 * {@code lateralClearanceMetres} is the room a rider insists on beside them,
 * {@code maxSpeedDifferentialMps} caps how much faster than the traffic being
 * passed they will go, and {@code maxFilteringSpeedMps} is the speed above
 * which riders stop filtering and use a lane.</p>
 *
 * @param lateralClearanceMetres   room required beside the rider, each side
 * @param maxSpeedDifferentialMps  how far above the surrounding traffic a
 *                                 filtering rider will travel
 * @param maxFilteringSpeedMps     absolute speed above which filtering stops
 * @param neighbourWindowMetres    how far along the road counts as alongside
 */
public record FilteringRules(
        double lateralClearanceMetres,
        double maxSpeedDifferentialMps,
        double maxFilteringSpeedMps,
        double neighbourWindowMetres) {

    public FilteringRules {
        requirePositive("Lateral clearance", lateralClearanceMetres);
        requirePositive("Speed differential", maxSpeedDifferentialMps);
        requirePositive("Maximum filtering speed", maxFilteringSpeedMps);
        requirePositive("Neighbour window", neighbourWindowMetres);
    }

    /**
     * Defaults for a motorcycle: 0.30 m of room each side, no more than about
     * 20 km/h faster than the traffic it passes, and no filtering above roughly
     * 50 km/h.
     */
    public static FilteringRules defaults() {
        return new FilteringRules(0.30, 6.0, 14.0, 15.0);
    }

    /**
     * Speed a rider may hold this step, given what they planned and how fast the
     * traffic they are passing is moving.
     *
     * <p>Applies only while the rider is actually between lanes. A motorcycle
     * that has settled on a lane centre is an ordinary vehicle and is limited by
     * the car-following model alone.</p>
     */
    public double cappedSpeedMps(
            double plannedSpeedMps, double slowestNeighbourSpeedMps, boolean filtering) {
        if (!filtering) {
            return plannedSpeedMps;
        }
        double differentialCap = slowestNeighbourSpeedMps + maxSpeedDifferentialMps;
        return Math.min(plannedSpeedMps, Math.min(differentialCap, maxFilteringSpeedMps));
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }
}
