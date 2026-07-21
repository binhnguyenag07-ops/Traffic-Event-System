package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import team.traffic.domain.network.Road;
import team.traffic.domain.vehicle.Vehicle;

/**
 * Physical state of one road: which vehicles are on it, where they are, and how
 * much tarmac they actually take up.
 *
 * <p>This is what makes congestion physical rather than nominal. The road's own
 * {@code capacity} is a plain vehicle count, so a 12 metre bus and a 4.5 metre
 * car each consume exactly one unit of it. Here a road is full when the vehicles
 * standing on it, each including the standstill gap its driver keeps, no longer
 * fit into the road's length. A road of a given length therefore holds far fewer
 * buses than cars, which is the behaviour a real queue has.</p>
 *
 * <p>Positions are metres from the road's start intersection and refer to the
 * front bumper. Instances describe one simulation step and are rebuilt each
 * tick by {@link TrafficState}.</p>
 */
public final class LaneOccupancy {

    private final Road road;
    private final double roadLengthMetres;
    /** Sorted by position ascending, so the vehicle nearest the exit is last. */
    private final List<Vehicle> vehicles;

    private LaneOccupancy(Road road, List<Vehicle> vehicles) {
        this.road = road;
        this.roadLengthMetres = road.getDistance() * 1_000.0;
        this.vehicles = vehicles;
    }

    /**
     * Captures the vehicles currently occupying {@code road}, ordered from the
     * entry of the road towards its exit.
     */
    public static LaneOccupancy of(Road road, Collection<Vehicle> candidates) {
        if (road == null) {
            throw new IllegalArgumentException("Road is required.");
        }

        List<Vehicle> onRoad = new ArrayList<>();
        if (candidates != null) {
            for (Vehicle vehicle : candidates) {
                if (road.getId().equals(vehicle.getOccupiedRoadId())) {
                    onRoad.add(vehicle);
                }
            }
        }
        // Ties are broken by id so the ordering never depends on list order.
        onRoad.sort(Comparator.comparingDouble(Vehicle::getRoadProgress)
                .thenComparing(Vehicle::getId));
        return new LaneOccupancy(road, onRoad);
    }

    public Road getRoad() {
        return road;
    }

    public double getRoadLengthMetres() {
        return roadLengthMetres;
    }

    public int getVehicleCount() {
        return vehicles.size();
    }

    public List<Vehicle> getVehicles() {
        return List.copyOf(vehicles);
    }

    /** Position of a vehicle's front bumper, in metres from the road start. */
    public double positionMetresOf(Vehicle vehicle) {
        return vehicle.getRoadProgress() * roadLengthMetres;
    }

    /**
     * Tarmac consumed by the vehicles on this road, in metres, counting each
     * vehicle's body plus the standstill gap its driver keeps.
     */
    public double getOccupiedLengthMetres() {
        double occupied = 0;
        for (Vehicle vehicle : vehicles) {
            occupied += vehicle.getDriverProfile().jamSpacingMetres(vehicle.getLength());
        }
        return occupied;
    }

    /**
     * Share of the road's physical length that is taken up, clamped to 0..1.
     *
     * <p>Unlike {@link Road#getCapacityUsage()} this reflects vehicle size, so
     * a lane of buses saturates long before a lane of cars.</p>
     */
    public double getPhysicalJamRatio() {
        if (roadLengthMetres <= 0) {
            return 1.0;
        }
        return Math.min(1.0, getOccupiedLengthMetres() / roadLengthMetres);
    }

    /** Free space between the road entry and the rearmost vehicle, in metres. */
    public double getEntryGapMetres() {
        if (vehicles.isEmpty()) {
            return roadLengthMetres;
        }
        Vehicle rearmost = vehicles.get(0);
        return Math.max(0.0, positionMetresOf(rearmost) - rearmost.getLength());
    }

    /**
     * Whether an arriving vehicle physically fits: the road must have room for
     * its body and standstill gap, and the space just past the entry must be
     * clear of the queue tail.
     */
    public boolean canAdmit(Vehicle arriving) {
        if (arriving == null) {
            return false;
        }
        double required = arriving.getDriverProfile().jamSpacingMetres(arriving.getLength());
        return getOccupiedLengthMetres() + required <= roadLengthMetres
                && getEntryGapMetres() >= required;
    }

    /**
     * Space and speed of the vehicle directly ahead of {@code follower}, or
     * {@link LeaderGap#FREE_ROAD} when the road ahead is clear.
     */
    public LeaderGap findLeaderFor(Vehicle follower) {
        if (follower == null) {
            return LeaderGap.FREE_ROAD;
        }

        double followerPosition = positionMetresOf(follower);
        Vehicle leader = null;
        for (Vehicle candidate : vehicles) {
            if (candidate == follower || candidate.getId().equals(follower.getId())) {
                continue;
            }
            double candidatePosition = positionMetresOf(candidate);
            if (candidatePosition < followerPosition) {
                continue;
            }
            if (leader == null || candidatePosition < positionMetresOf(leader)) {
                leader = candidate;
            }
        }

        if (leader == null) {
            return LeaderGap.FREE_ROAD;
        }

        double bumperToBumper = positionMetresOf(leader) - leader.getLength() - followerPosition;
        double usableGap = bumperToBumper - follower.getDriverProfile().minimumGapMetres();
        return new LeaderGap(usableGap, leader.getInstantaneousSpeedMps());
    }

    /** Remaining distance from a vehicle's front bumper to the road exit. */
    public double distanceToExitMetres(Vehicle vehicle) {
        return Math.max(0.0, roadLengthMetres - positionMetresOf(vehicle));
    }
}
