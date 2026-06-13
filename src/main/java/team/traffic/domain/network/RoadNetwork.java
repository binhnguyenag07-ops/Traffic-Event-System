package main.java.team.traffic.domain.network;

import java.util.HashMap;
import java.util.Map;

//class RoadNetwork {
    //     -Map~String, Intersection~ intersections
    //     -Map~String, Road~ roads
    //     +addIntersection(Intersection)
    //     +removeIntersection(String)
    //     +addRoad(Road)
    //     +removeRoad(String)
    //     +findOutgoingRoads(String) List~Road~
    //     +findIncomingRoads(String) List~Road~
    //     +updateRoadCondition(String, RoadCondition)
    // }

    
public class RoadNetwork {
    private Map<String, Intersection> intersections;
    private Map<String, Road> roads;

    public RoadNetwork() {
        this.intersections = new HashMap<>();
        this.roads = new HashMap<>();
    }

    public Map<String, Intersection> addIntersection(Intersection intersection) {
        return intersections;
    }

    public Map<String, Intersection> removeIntersection(String intersectionName) {
        return intersections;
    }

    public Map<String, Road> addRoad(Road road) {
        return roads;
    }

    public Map<String, Road> removeRoad(String roadName) {
        return roads;
    }
    
}