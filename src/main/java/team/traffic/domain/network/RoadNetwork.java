package team.traffic.domain.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoadNetwork {
    private final List<Intersection> intersections = new ArrayList<>();
    private final List<Road> roads = new ArrayList<>();

    public void addIntersection(Intersection intersection) {
        // TODO: validate duplicate id before adding.
        intersections.add(intersection);
    }

    public void removeIntersection(String intersectionId) {
        // TODO: also remove connected roads.
        intersections.removeIf(intersection -> intersection.getId().equals(intersectionId));
    }

    public Intersection findIntersectionById(String intersectionId) {
        return intersections.stream()
                .filter(intersection -> intersection.getId().equals(intersectionId))
                .findFirst()
                .orElse(null);
    }

    public List<Intersection> getIntersections() {
        return Collections.unmodifiableList(intersections);
    }

    public void addRoad(Road road) {
        // TODO: validate start/end intersections exist.
        roads.add(road);
    }

    public void removeRoad(String roadId) {
        roads.removeIf(road -> road.getId().equals(roadId));
    }

    public Road findRoadById(String roadId) {
        return roads.stream()
                .filter(road -> road.getId().equals(roadId))
                .findFirst()
                .orElse(null);
    }

    public List<Road> getRoads() {
        return Collections.unmodifiableList(roads);
    }

    public List<Road> findOutgoingRoads(String intersectionId) {
        List<Road> result = new ArrayList<>();
        for (Road road : roads) {
            if (road.getStartIntersectionId().equals(intersectionId)) {
                result.add(road);
            }
        }
        return result;
    }

    public List<Road> findIncomingRoads(String intersectionId) {
        List<Road> result = new ArrayList<>();
        for (Road road : roads) {
            if (road.getEndIntersectionId().equals(intersectionId)) {
                result.add(road);
            }
        }
        return result;
    }

    public void updateRoadCondition(String roadId, RoadCondition condition) {
        // TODO: add setter on Road or replace road condition through richer domain method.
        Road road = findRoadById(roadId);
        if (road != null && condition != null) {
            road.getCondition().setCongestionLevel(condition.getCongestionLevel());
            road.getCondition().setVehicleDensity(condition.getVehicleDensity());
            road.getCondition().setStatus(condition.getStatus());
        }
    }
}
