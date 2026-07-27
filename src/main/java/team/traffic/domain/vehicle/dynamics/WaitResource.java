package team.traffic.domain.vehicle.dynamics;

/** A stable resource identifier suitable for a deterministic wait-for graph. */
public sealed interface WaitResource permits
        WaitResource.Leader,
        WaitResource.LaneEntry,
        WaitResource.JunctionMovement,
        WaitResource.RoadExit,
        WaitResource.LocalObstruction {

    String stableKey();

    record Leader(String vehicleId) implements WaitResource {
        public Leader {
            requireId(vehicleId, "Leader vehicle id");
        }
        @Override public String stableKey() { return "LEADER:" + vehicleId; }
    }

    record LaneEntry(String roadId, int laneIndex) implements WaitResource {
        public LaneEntry {
            requireId(roadId, "Road id");
            if (laneIndex < 0) throw new IllegalArgumentException("Lane index must not be negative.");
        }
        @Override public String stableKey() { return "LANE_ENTRY:" + roadId + ":" + laneIndex; }
    }

    record JunctionMovement(
            String junctionId,
            String fromRoadId,
            String toRoadId) implements WaitResource {
        public JunctionMovement {
            requireId(junctionId, "Junction id");
            requireId(toRoadId, "Target road id");
            fromRoadId = fromRoadId == null ? "DEPARTURE" : fromRoadId;
        }
        @Override public String stableKey() {
            return "JUNCTION_MOVEMENT:" + junctionId + ":" + fromRoadId + ">" + toRoadId;
        }
    }

    record RoadExit(String roadId) implements WaitResource {
        public RoadExit {
            requireId(roadId, "Road id");
        }
        @Override public String stableKey() { return "ROAD_EXIT:" + roadId; }
    }

    record LocalObstruction(String eventId) implements WaitResource {
        public LocalObstruction {
            requireId(eventId, "Event id");
        }
        @Override public String stableKey() { return "LOCAL_OBSTRUCTION:" + eventId; }
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }
}
