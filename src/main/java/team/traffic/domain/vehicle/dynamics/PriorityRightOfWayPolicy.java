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
     * After waiting this many ticks a vehicle is promoted above hierarchy and
     * turn rules, which prevents a minor approach from being starved forever.
     */
    public static final long STARVATION_TICKS = 6;

    private final long starvationTicks;

    public PriorityRightOfWayPolicy() {
        this(STARVATION_TICKS);
    }

    public PriorityRightOfWayPolicy(long starvationTicks) {
        if (starvationTicks < 0) {
            throw new IllegalArgumentException("Starvation threshold must not be negative.");
        }
        this.starvationTicks = starvationTicks;
    }

    @Override
    public int comparePriority(JunctionApproach first, JunctionApproach second) {
        int byDispatchPriority = Integer.compare(
                second.vehicle().getPriorityLevel(),
                first.vehicle().getPriorityLevel());
        if (byDispatchPriority != 0) {
            return byDispatchPriority;
        }

        int byStarvation = Boolean.compare(isStarved(second), isStarved(first));
        if (byStarvation != 0) {
            return byStarvation;
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
        return approach.waitedTicks() >= starvationTicks;
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
