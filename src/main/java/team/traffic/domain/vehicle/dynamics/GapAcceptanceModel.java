package team.traffic.domain.vehicle.dynamics;

/**
 * Decides whether a driver is willing to pull out into a junction.
 *
 * <p>A vehicle going straight on, or turning into the near side, merges with
 * traffic rather than cutting across it, so ordering by right of way is enough.
 * A vehicle turning across the opposing stream has to judge a gap: it goes only
 * when the nearest conflicting vehicle is further away in time than the headway
 * its driver insists on. Emergency drivers accept much smaller gaps than bus
 * drivers, which falls out of {@link DriverProfile#gapAcceptanceSeconds()}.</p>
 */
public final class GapAcceptanceModel {

    /**
     * Whether {@code approach} may enter the junction given the surrounding
     * traffic.
     */
    public boolean hasAcceptableGap(JunctionApproach approach, JunctionEnvironment environment) {
        if (approach == null || environment == null) {
            return false;
        }
        if (!approach.turnDirection().crossesOpposingTraffic()) {
            return true;
        }

        String ownApproachRoadId = approach.fromRoad() == null ? null : approach.fromRoad().getId();
        double availableGapSeconds =
                environment.secondsUntilNextArrival(approach.junctionId(), ownApproachRoadId);
        return availableGapSeconds >= approach.vehicle().getDriverProfile().gapAcceptanceSeconds();
    }
}
