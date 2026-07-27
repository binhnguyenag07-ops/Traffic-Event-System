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
    private final Map<String, Building> buildings;
    private final List<LandCover> landCovers;
    private final JunctionMovementGraph movementGraph;

    public RoadNetwork() {
        this.intersections = new LinkedHashMap<>();
        this.roads = new LinkedHashMap<>();
        this.outgoingRoads = new LinkedHashMap<>();
        this.incomingRoads = new LinkedHashMap<>();
        this.buildings = new LinkedHashMap<>();
        this.landCovers = new ArrayList<>();
        this.movementGraph = new JunctionMovementGraph(this);
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
        boolean usedByBuilding = buildings.values().stream()
                .flatMap(building -> building.getAccessPoints().stream())
                .anyMatch(access -> access.intersectionId().equals(intersectionId));
        if (usedByBuilding) {
            throw new IllegalStateException("Intersection is used by a building access point: " + intersectionId);
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
        movementGraph.removeRulesForRoad(roadId);
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

    /** Returns both directed sides, when present, of one physical road. */
    public List<Road> findRoadsByPhysicalRoadId(String physicalRoadId) {
        if (physicalRoadId == null || physicalRoadId.isBlank()) {
            throw new IllegalArgumentException("Physical road id is required");
        }
        return roads.values().stream()
                .filter(road -> physicalRoadId.equals(road.getPhysicalRoadId()))
                .toList();
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

    /** The single movement graph shared by routing, dynamics, and scene geometry. */
    public JunctionMovementGraph getMovementGraph() {
        return movementGraph;
    }

    public void addBuilding(Building building) {
        if (building == null) throw new IllegalArgumentException("Building is required");
        if (buildings.containsKey(building.getId())) {
            throw new IllegalArgumentException("Duplicate building id: " + building.getId());
        }
        for (BuildingAccessPoint access : building.getAccessPoints()) {
            if (!containsIntersection(access.intersectionId())) {
                throw new IllegalArgumentException(
                        "Building access intersection does not exist: " + access.intersectionId());
            }
        }
        buildings.put(building.getId(), building);
    }

    public void removeBuilding(String buildingId) {
        buildings.remove(buildingId);
    }

    public Building getBuilding(String buildingId) {
        return buildings.get(buildingId);
    }

    public boolean containsBuilding(String buildingId) {
        return buildings.containsKey(buildingId);
    }

    public int getBuildingCount() {
        return buildings.size();
    }

    public Map<String, Building> getBuildings() {
        return Collections.unmodifiableMap(buildings);
    }

    /**
     * Adds a patch of ground. Land cover is drawn, never routed over, so it is
     * kept as a plain list: nothing looks one up by id.
     */
    public void addLandCover(LandCover landCover) {
        if (landCover == null) {
            throw new IllegalArgumentException("Land cover is required");
        }
        landCovers.add(landCover);
    }

    public List<LandCover> getLandCovers() {
        return Collections.unmodifiableList(landCovers);
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
}
