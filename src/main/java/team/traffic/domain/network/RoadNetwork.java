package team.traffic.domain.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
    
public class RoadNetwork {
    private final Map<String, Intersection> intersections;
    private final Map<String, Road> roads;
    private final Map<String, List<Road>> outgoingRoads;
    private final Map<String, List<Road>> incomingRoads;

    public RoadNetwork() {
        this.intersections = new LinkedHashMap<>();
        this.roads = new LinkedHashMap<>();
        this.outgoingRoads = new LinkedHashMap<>();
        this.incomingRoads = new LinkedHashMap<>();
    }

    // Adds an intersection while preserving the graph-wide unique-id invariant.
    public void addIntersection(Intersection intersection) {
        if (intersection == null) {
            throw new IllegalArgumentException("Intersection is required");
        }
        if (intersections.containsKey(intersection.getId())) {
            throw new IllegalArgumentException("Duplicate intersection id: " + intersection.getId());
        }
        intersections.put(intersection.getId(), intersection);
        outgoingRoads.put(intersection.getId(), new ArrayList<>());
        incomingRoads.put(intersection.getId(), new ArrayList<>());
    }

    // Removes the intersection and any roads connected to it.
    public void removeIntersection(String intersectionId) {
        if (!intersections.containsKey(intersectionId)) {
            return;
        }

        Set<String> connectedRoadIds = new LinkedHashSet<>();
        for (Road road : outgoingRoads.get(intersectionId)) {
            connectedRoadIds.add(road.getId());
        }
        for (Road road : incomingRoads.get(intersectionId)) {
            connectedRoadIds.add(road.getId());
        }
        for (String roadId : connectedRoadIds) {
            removeRoad(roadId);
        }

        intersections.remove(intersectionId);
        outgoingRoads.remove(intersectionId);
        incomingRoads.remove(intersectionId);
    }

    // Adds a road only when both endpoint intersections already exist.
    public void addRoad(Road road) {
        if (road == null) {
            throw new IllegalArgumentException("Road is required");
        }
        if (roads.containsKey(road.getId())) {
            throw new IllegalArgumentException("Duplicate road id: " + road.getId());
        }
        if (!intersections.containsKey(road.getStartIntersectionId())) {
            throw new IllegalArgumentException("Start intersection does not exist: " + road.getStartIntersectionId());
        }
        if (!intersections.containsKey(road.getEndIntersectionId())) {
            throw new IllegalArgumentException("End intersection does not exist: " + road.getEndIntersectionId());
        }
        validateRoadGeometry(road);
        roads.put(road.getId(), road);
        outgoingRoads.get(road.getStartIntersectionId()).add(road);
        incomingRoads.get(road.getEndIntersectionId()).add(road);
    }

    // Removes one road from the network by id.
    public void removeRoad(String roadId) {
        Road removedRoad = roads.remove(roadId);
        if (removedRoad == null) {
            return;
        }

        outgoingRoads.get(removedRoad.getStartIntersectionId()).remove(removedRoad);
        incomingRoads.get(removedRoad.getEndIntersectionId()).remove(removedRoad);
    }

    // Finds all directed roads starting from the given intersection.
    public List<Road> findOutgoingRoads(String intersectionId) {
        List<Road> roadsFromIntersection = outgoingRoads.get(intersectionId);
        return roadsFromIntersection == null ? List.of() : List.copyOf(roadsFromIntersection);
    }

    // Finds all directed roads ending at the given intersection.
    public List<Road> findIncomingRoads(String intersectionId) {
        List<Road> roadsToIntersection = incomingRoads.get(intersectionId);
        return roadsToIntersection == null ? List.of() : List.copyOf(roadsToIntersection);
    }

    // Returns unique destination intersections reachable by one outgoing road.
    public List<Intersection> findOutgoingNeighbors(String intersectionId) {
        Set<Intersection> neighbors = new LinkedHashSet<>();
        for (Road road : findOutgoingRoads(intersectionId)) {
            neighbors.add(intersections.get(road.getEndIntersectionId()));
        }
        return List.copyOf(neighbors);
    }

    /** Returns both directed sides (when present) of one physical road segment. */
    public List<Road> findRoadsByPhysicalRoadId(String physicalRoadId) {
        if (physicalRoadId == null || physicalRoadId.isBlank()) {
            throw new IllegalArgumentException("Physical road id is required");
        }

        List<Road> matchingRoads = new ArrayList<>();
        for (Road road : roads.values()) {
            if (physicalRoadId.equals(road.getPhysicalRoadId())) {
                matchingRoads.add(road);
            }
        }
        return List.copyOf(matchingRoads);
    }

    // Replaces the current condition of one road.
    public void updateRoadCondition(String roadId, RoadCondition condition) {
        Road road = requireRoad(roadId);
        road.setCondition(condition);
    }

    public Road recordVehicleEnteringRoad(String roadId) {
        Road road = requireRoad(roadId);
        road.enterVehicle();
        return road;
    }

    public Road recordVehicleLeavingRoad(String roadId) {
        Road road = requireRoad(roadId);
        road.leaveVehicle();
        return road;
    }

    public double updateVehicleDensity(String roadId) {
        return requireRoad(roadId).refreshVehicleDensity();
    }

    public double getAverageVehicleDensity() {
        if (roads.isEmpty()) {
            return 0;
        }

        double totalDensity = 0;
        for (Road road : roads.values()) {
            totalDensity += road.getCondition().getVehicleDensity();
        }
        return totalDensity / roads.size();
    }

    public List<Road> findRoadsAtOrAboveDensity(double minimumDensity) {
        validateDensityThreshold(minimumDensity);

        List<Road> denseRoads = new ArrayList<>();
        for (Road road : roads.values()) {
            if (road.getCondition().getVehicleDensity() >= minimumDensity) {
                denseRoads.add(road);
            }
        }
        return List.copyOf(denseRoads);
    }

    /**
     * Returns roads with the requested status in network insertion order.
     * Consumers can use this read-only query for legends, filters, and map
     * layers without duplicating network traversal logic.
     */
    public List<Road> findRoadsByStatus(RoadStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Road status is required");
        }

        List<Road> matchingRoads = new ArrayList<>();
        for (Road road : roads.values()) {
            if (road.getCondition().getRoadStatus() == status) {
                matchingRoads.add(road);
            }
        }
        return List.copyOf(matchingRoads);
    }

    /**
     * Counts every road status, including statuses with zero roads. The
     * returned map is read-only and can be passed to statistics or
     * visualization layers without exposing the mutable road index.
     */
    public Map<RoadStatus, Integer> getRoadStatusCounts() {
        Map<RoadStatus, Integer> counts = new EnumMap<>(RoadStatus.class);
        for (RoadStatus status : RoadStatus.values()) {
            counts.put(status, 0);
        }
        for (Road road : roads.values()) {
            RoadStatus status = road.getCondition().getRoadStatus();
            counts.put(status, counts.get(status) + 1);
        }
        return Collections.unmodifiableMap(counts);
    }

    /** Returns the mean normalized congestion level across all roads. */
    public double getAverageCongestionLevel() {
        if (roads.isEmpty()) {
            return 0;
        }

        double totalCongestion = 0;
        for (Road road : roads.values()) {
            totalCongestion += road.getCondition().getCongestionLevel();
        }
        return totalCongestion / roads.size();
    }

    public Intersection getIntersection(String intersectionId) {
        return intersections.get(intersectionId);
    }

    public Road getRoad(String roadId) {
        return roads.get(roadId);
    }

    public boolean containsIntersection(String intersectionId) {
        return intersections.containsKey(intersectionId);
    }

    public boolean containsRoad(String roadId) {
        return roads.containsKey(roadId);
    }

    public int getIntersectionCount() {
        return intersections.size();
    }

    public int getRoadCount() {
        return roads.size();
    }

    public Map<String, Intersection> getIntersections() {
        return Collections.unmodifiableMap(intersections);
    }

    public Map<String, Road> getRoads() {
        return Collections.unmodifiableMap(roads);
    }

    private Road requireRoad(String roadId) {
        Road road = roads.get(roadId);
        if (road == null) {
            throw new IllegalArgumentException("Road does not exist: " + roadId);
        }
        return road;
    }

    private void validateDensityThreshold(double minimumDensity) {
        if (!Double.isFinite(minimumDensity) || minimumDensity < 0 || minimumDensity > 1) {
            throw new IllegalArgumentException("Minimum density must be finite and between 0 and 1");
        }
    }

    private void validateRoadGeometry(Road road) {
        if (!road.hasGeometry()) {
            return;
        }

        Intersection start = intersections.get(road.getStartIntersectionId());
        Intersection end = intersections.get(road.getEndIntersectionId());
        GeoPoint firstPoint = road.getGeometry().get(0);
        GeoPoint lastPoint = road.getGeometry().get(road.getGeometry().size() - 1);
        if (!matches(firstPoint, start)) {
            throw new IllegalArgumentException("Road geometry must start at intersection: " + start.getId());
        }
        if (!matches(lastPoint, end)) {
            throw new IllegalArgumentException("Road geometry must end at intersection: " + end.getId());
        }
    }

    private boolean matches(GeoPoint point, Intersection intersection) {
        double tolerance = 0.000001;
        return Math.abs(point.longitude() - intersection.getLongitude()) <= tolerance
                && Math.abs(point.latitude() - intersection.getLatitude()) <= tolerance;
    }
}
