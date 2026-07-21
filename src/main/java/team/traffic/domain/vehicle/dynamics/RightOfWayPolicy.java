package team.traffic.domain.vehicle.dynamics;

/**
 * Decides who goes first at a junction and which movements cannot be made at
 * the same time.
 *
 * <p>Kept as an interface so a scenario can swap in a different rule set, for
 * example all-way-stop or signal-controlled, without changing the vehicles or
 * the simulator.</p>
 */
public interface RightOfWayPolicy {

    /**
     * Orders two vehicles waiting at the same junction. Returns a negative
     * number when {@code first} should be let through before {@code second},
     * following the {@link java.util.Comparator} convention. The ordering must
     * be total and deterministic so a run can be reproduced.
     */
    int comparePriority(JunctionApproach first, JunctionApproach second);

    /**
     * Whether two movements cannot be performed at the same time, either
     * because their paths cross or because they merge onto the same road.
     */
    boolean conflicts(JunctionApproach first, JunctionApproach second);

    /** Identifier used in reports and traces. */
    String getName();
}
