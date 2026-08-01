package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;

/**
 * Right-of-way based on road hierarchy, mirroring how SUMO assigns priority
 * from the street type of each approach.
 *
 * <p>Vehicles are let through in this order:</p>
 * <ol>
 *   <li>emergency vehicles first, highest dispatch priority leading;</li>
 *   <li>then traffic that has been waiting long enough to be starved, so a
 *       vehicle on a side street eventually gets out even against a steady
 *       stream on the main road;</li>
 *   <li>among the starved, whoever has been stuck longest without moving, so a
 *       saturated junction rotates between its arms instead of settling on one;</li>
 *   <li>then traffic arriving on the more important street;</li>
 *   <li>then straight-ahead movements before turns, since a turn crosses or
 *       merges with another stream;</li>
 *   <li>then whoever is closest to the stop line;</li>
 *   <li>finally by vehicle id, purely so the result is reproducible.</li>
 * </ol>
 *
 * <p>Two movements conflict when they merge onto the same road, or when either
 * one turns across the other's path. Two straight-ahead movements from
 * different approaches are treated as conflicting as well, because this network
 * model has no lane-level geometry to prove they would miss each other.</p>
 */
public final class PriorityRightOfWayPolicy implements RightOfWayPolicy {

    /**
     * After being stationary on one approach for this many simulation seconds,
     * a vehicle is promoted above hierarchy and turn rules. Using elapsed time
     * keeps fairness stable when the dynamics sub-step size changes.
     */
    public static final double STARVATION_SECONDS = 6.0;

    private final double starvationSeconds;

    public PriorityRightOfWayPolicy() {
        this(STARVATION_SECONDS);
    }

    public PriorityRightOfWayPolicy(double starvationSeconds) {
        if (!Double.isFinite(starvationSeconds) || starvationSeconds < 0) {
            throw new IllegalArgumentException(
                    "Starvation threshold must be finite and not negative.");
        }
        this.starvationSeconds = starvationSeconds;
    }

    @Override
    public int comparePriority(JunctionApproach first, JunctionApproach second) {
        int byDispatchPriority = Integer.compare(
                second.vehicle().getPriorityLevel(),
                first.vehicle().getPriorityLevel());
        if (byDispatchPriority != 0) {
            return byDispatchPriority;
        }

        boolean firstStarved = isStarved(first);
        boolean secondStarved = isStarved(second);
        int byStarvation = Boolean.compare(secondStarved, firstStarved);
        if (byStarvation != 0) {
            return byStarvation;
        }

        // Once a junction saturates, every approach is starved and the flag
        // above stops separating anyone: the order falls through to road class,
        // manoeuvre and vehicle id, all of which are fixed. The same arm then
        // wins every step and the others are held indefinitely. Ranking the
        // starved by how long they have actually been stuck turns that into a
        // rotation, so a queue drains in the order it formed.
        if (firstStarved) {
            int byWaitLength = Double.compare(
                    second.queueWaitSeconds(), first.queueWaitSeconds());
            if (byWaitLength != 0) {
                return byWaitLength;
            }
        }

        int byRoadImportance = Integer.compare(
                importanceOf(first.fromRoad()),
                importanceOf(second.fromRoad()));
        if (byRoadImportance != 0) {
            return byRoadImportance;
        }

        int byManoeuvre = Integer.compare(
                manoeuvreRank(first.turnDirection()),
                manoeuvreRank(second.turnDirection()));
        if (byManoeuvre != 0) {
            return byManoeuvre;
        }

        int byDistance = Double.compare(
                first.distanceToJunctionMetres(),
                second.distanceToJunctionMetres());
        if (byDistance != 0) {
            return byDistance;
        }

        return first.vehicleId().compareTo(second.vehicleId());
    }

    @Override
    public boolean conflicts(JunctionApproach first, JunctionApproach second) {
        if (first.vehicleId().equals(second.vehicleId())) {
            return false;
        }
        if (first.toRoad().getId().equals(second.toRoad().getId())) {
            return true;
        }
        if (sameApproach(first, second)) {
            // Queued behind one another rather than crossing.
            return false;
        }
        return true;
    }

    @Override
    public String getName() {
        return "RoadPriority";
    }

    private boolean isStarved(JunctionApproach approach) {
        return approach.queueWaitSeconds() >= starvationSeconds;
    }

    private static boolean sameApproach(JunctionApproach first, JunctionApproach second) {
        if (first.fromRoad() == null || second.fromRoad() == null) {
            return first.fromRoad() == second.fromRoad();
        }
        return first.fromRoad().getId().equals(second.fromRoad().getId());
    }

    /** Lower is more important. A missing approach road ranks lowest. */
    private static int importanceOf(Road road) {
        if (road == null) {
            return Integer.MAX_VALUE;
        }
        RoadClass roadClass = road.getRoadClass();
        return switch (roadClass == null ? RoadClass.UNCLASSIFIED : roadClass) {
            case PRIMARY -> 0;
            case SECONDARY -> 1;
            case TERTIARY -> 2;
            case UNCLASSIFIED -> 3;
            case RESIDENTIAL -> 4;
            case LIVING_STREET -> 5;
        };
    }

    /** Lower goes first. */
    private static int manoeuvreRank(TurnDirection direction) {
        return switch (direction) {
            case STRAIGHT -> 0;
            case RIGHT -> 1;
            case LEFT -> 2;
            case U_TURN -> 3;
        };
    }
}
