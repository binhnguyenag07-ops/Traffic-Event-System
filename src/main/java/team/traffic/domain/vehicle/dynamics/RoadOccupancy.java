package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Road;

/** Physical state of every lane on one directed road. */
public final class RoadOccupancy {
    private final Road road;
    private final List<Lane> lanes;
    private final double laneWidthMetres;

    static RoadOccupancy of(
            Road road,
            List<TrafficParticipant> vehicles,
            RoadLayout roadLayout,
            List<TrafficEvent> events) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        if (roadLayout == null) throw new IllegalArgumentException("Road layout is required.");
        int laneCount = roadLayout.laneCountOf(road);
        List<Lane> lanes = new ArrayList<>(laneCount);
        for (int index = 0; index < laneCount; index++) {
            int laneIndex = index;
            List<TrafficParticipant> inLane = vehicles.stream()
                    .filter(vehicle -> vehicle.getLaneIndex() == laneIndex)
                    .toList();
            lanes.add(new Lane(road, index, inLane, events));
        }
        return new RoadOccupancy(road, lanes, roadLayout.laneWidthMetresOf(road));
    }

    private RoadOccupancy(Road road, List<Lane> lanes, double laneWidthMetres) {
        this.road = road;
        this.lanes = List.copyOf(lanes);
        this.laneWidthMetres = laneWidthMetres;
    }

    public Road getRoad() { return road; }
    public int getLaneCount() { return lanes.size(); }
    public List<Lane> getLanes() { return lanes; }
    public double getLaneWidthMetres() { return laneWidthMetres; }

    /**
     * Signed offset of a lane's centre from the road centreline, in metres.
     *
     * <p>Carried here so a vehicle can steer towards its target lane without
     * having to be handed the road layout as well as its occupancy.</p>
     */
    public double laneCentreOffsetMetres(int laneIndex) {
        return RoadLayout.laneCentreOffsetMetres(laneIndex, lanes.size(), laneWidthMetres);
    }

    public Lane lane(int laneIndex) {
        if (laneIndex < 0 || laneIndex >= lanes.size()) return null;
        return lanes.get(laneIndex);
    }

    public Lane laneOf(TrafficParticipant vehicle) {
        if (vehicle == null) return null;
        Lane lane = lane(vehicle.getLaneIndex());
        return lane == null ? lanes.get(0) : lane;
    }

    public boolean canAdmit(TrafficParticipant arriving) {
        return lanes.stream().anyMatch(lane -> lane.canAdmit(arriving));
    }

    /** Lane with the largest clear entry gap, with index as deterministic tie-breaker. */
    public int bestLaneForEntry(TrafficParticipant arriving) {
        Lane best = null;
        for (Lane lane : lanes) {
            if (!lane.canAdmit(arriving)) continue;
            if (best == null || lane.getEntryGapMetres() > best.getEntryGapMetres()) best = lane;
        }
        return best == null ? -1 : best.getLaneIndex();
    }

    public double getPhysicalJamRatio() {
        return lanes.stream().mapToDouble(Lane::getPhysicalJamRatio).average().orElse(0.0);
    }

    public int getVehicleCount() {
        return lanes.stream().mapToInt(Lane::getVehicleCount).sum();
    }

    public List<TrafficParticipant> getVehicles() {
        return lanes.stream().flatMap(lane -> lane.getVehicles().stream()).toList();
    }
}
