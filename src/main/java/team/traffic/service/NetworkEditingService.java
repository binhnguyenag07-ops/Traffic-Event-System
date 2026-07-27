package team.traffic.service;

import java.io.IOException;
import java.util.List;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.network.JunctionMovement;

public interface NetworkEditingService {
    Intersection addIntersection(String id, String name, LocationType type, double latitude, double longitude);
    void removeIntersection(String id);
    RoadCreationOutcome addRoads(RoadCreationRequest request);
    void removeRoad(String id);
    Road updateRoadCondition(String id, RoadStatus status, double congestionLevel);
    ConnectedRoads findConnectedRoads(String intersectionId);
    String exportJson() throws IOException;
    default JunctionMovement upsertMovement(JunctionMovement movement) {
        throw new UnsupportedOperationException("Movement editing is not configured");
    }
    default boolean removeMovement(
            String junctionId,
            String fromRoadId,
            String toRoadId) {
        throw new UnsupportedOperationException("Movement editing is not configured");
    }

    record RoadCreationRequest(String id, String reverseRoadId, String startIntersectionId,
            String endIntersectionId, double speedLimit, int capacity, boolean bidirectional,
            RoadClass roadClass, Integer laneCount, Double laneWidthMetres,
            String name, String physicalRoadId) {
        public RoadCreationRequest(String id, String reverseRoadId, String startIntersectionId,
                String endIntersectionId, double speedLimit, int capacity, boolean bidirectional) {
            this(id, reverseRoadId, startIntersectionId, endIntersectionId,
                    speedLimit, capacity, bidirectional, RoadClass.UNCLASSIFIED,
                    null, null, null, null);
        }

        public RoadCreationRequest(String id, String reverseRoadId, String startIntersectionId,
                String endIntersectionId, double speedLimit, int capacity, boolean bidirectional,
                RoadClass roadClass) {
            this(id, reverseRoadId, startIntersectionId, endIntersectionId,
                    speedLimit, capacity, bidirectional, roadClass, null, null, null, null);
        }

        public RoadCreationRequest {
            roadClass = roadClass == null ? RoadClass.UNCLASSIFIED : roadClass;
            laneCount = laneCount == null ? roadClass.getDefaultLaneCount() : laneCount;
            laneWidthMetres = laneWidthMetres == null
                    ? roadClass.getDefaultLaneWidthMetres()
                    : laneWidthMetres;
            name = name == null || name.isBlank() ? id : name;
            physicalRoadId = physicalRoadId == null || physicalRoadId.isBlank() ? id : physicalRoadId;
        }
    }
    record RoadCreationOutcome(List<Road> roads, List<String> geometrySources) {}
    record ConnectedRoads(List<Road> incoming, List<Road> outgoing) {}
}
