package team.traffic.domain.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
    
public class RoadNetwork {
    private final Map<String, Intersection> intersections;
    private final Map<String, Road> roads;

    public RoadNetwork() {
        this.intersections = new HashMap<>();
        this.roads = new HashMap<>();
    }

    // Adds or replaces an intersection by its id.
    public void addIntersection(Intersection intersection) {
        if (intersection == null) {
            throw new IllegalArgumentException("Intersection is required");
        }
        intersections.put(intersection.getId(), intersection);
    }

    // Removes the intersection and any roads connected to it.
    public void removeIntersection(String intersectionId) {
        if (!intersections.containsKey(intersectionId)) {
            return;
        }

        intersections.remove(intersectionId);
        roads.entrySet().removeIf(entry -> {
            Road road = entry.getValue();
            return road.getStartIntersectionId().equals(intersectionId)
                    || road.getEndIntersectionId().equals(intersectionId);
        });
    }

    // Adds a road only when both endpoint intersections already exist.
    public void addRoad(Road road) {
        if (road == null) {
            throw new IllegalArgumentException("Road is required");
        }
        if (!intersections.containsKey(road.getStartIntersectionId())) {
            throw new IllegalArgumentException("Start intersection does not exist: " + road.getStartIntersectionId());
        }
        if (!intersections.containsKey(road.getEndIntersectionId())) {
            throw new IllegalArgumentException("End intersection does not exist: " + road.getEndIntersectionId());
        }
        roads.put(road.getId(), road);
    }

    // Removes one road from the network by id.
    public void removeRoad(String roadId) {
        roads.remove(roadId);
    }

    // Finds all directed roads starting from the given intersection.
    public List<Road> findOutgoingRoads(String intersectionId) {
        List<Road> outgoingRoads = new ArrayList<>();
        for (Road road : roads.values()) {
            if (road.getStartIntersectionId().equals(intersectionId)) {
                outgoingRoads.add(road);
            }
        }
        return outgoingRoads;
    }

    // Finds all directed roads ending at the given intersection.
    public List<Road> findIncomingRoads(String intersectionId) {
        List<Road> incomingRoads = new ArrayList<>();
        for (Road road : roads.values()) {
            if (road.getEndIntersectionId().equals(intersectionId)) {
                incomingRoads.add(road);
            }
        }
        return incomingRoads;
    }

    // Replaces the current condition of one road.
    public void updateRoadCondition(String roadId, RoadCondition condition) {
        Road road = roads.get(roadId);
        if (road == null) {
            throw new IllegalArgumentException("Road does not exist: " + roadId);
        }
        road.setCondition(condition);
    }

    public Intersection getIntersection(String intersectionId) {
        return intersections.get(intersectionId);
    }

    public Road getRoad(String roadId) {
        return roads.get(roadId);
    }

    public Map<String, Intersection> getIntersections() {
        return Collections.unmodifiableMap(intersections);
    }

    public Map<String, Road> getRoads() {
        return Collections.unmodifiableMap(roads);
    }
}
