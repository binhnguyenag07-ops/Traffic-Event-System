package team.traffic.service;

import java.io.IOException;
import java.util.List;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadStatus;

public interface NetworkEditingService {
    Intersection addIntersection(String id, String name, LocationType type, double latitude, double longitude);
    void removeIntersection(String id);
    RoadCreationOutcome addRoads(RoadCreationRequest request);
    void removeRoad(String id);
    Road updateRoadCondition(String id, RoadStatus status, double congestionLevel);
    ConnectedRoads findConnectedRoads(String intersectionId);
    String exportJson() throws IOException;

    record RoadCreationRequest(String id, String reverseRoadId, String startIntersectionId,
            String endIntersectionId, double speedLimit, int capacity, boolean bidirectional) {}
    record RoadCreationOutcome(List<Road> roads, List<String> geometrySources) {}
    record ConnectedRoads(List<Road> incoming, List<Road> outgoing) {}
}
