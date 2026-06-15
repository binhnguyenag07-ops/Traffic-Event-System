package team.traffic.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

public class JsonMapLoader implements MapLoader {
    private final ObjectMapper objectMapper;

    public JsonMapLoader() {
        this.objectMapper = new ObjectMapper();
    }

    // Reads intersections first, then roads, so road endpoints can be validated.
    @Override
    public RoadNetwork load(String filePath) throws IOException {
        MapData mapData = objectMapper.readValue(Path.of(filePath).toFile(), MapData.class);
        RoadNetwork roadNetwork = new RoadNetwork();

        if (mapData.intersections != null) {
            for (IntersectionData intersection : mapData.intersections) {
                roadNetwork.addIntersection(intersection.toIntersection());
            }
        }

        if (mapData.roads != null) {
            for (RoadData road : mapData.roads) {
                roadNetwork.addRoad(road.toRoad());
            }
        }

        return roadNetwork;
    }

    static class MapData {
        public List<IntersectionData> intersections;
        public List<RoadData> roads;
    }

    static class IntersectionData {
        public String id;
        public String name;
        public double x;
        public double y;
        public LocationType locationType;

        // Converts JSON data into the domain model.
        public Intersection toIntersection() {
            return new Intersection(id, name, x, y, locationType);
        }
    }

    static class RoadData {
        public String id;
        public String startIntersectionId;
        public String endIntersectionId;
        public double distance;
        public double speedLimit;
        public int capacity;
        public int currentVehicleCount;
        public double congestionLevel;
        public double vehicleDensity;
        public RoadStatus roadStatus;

        // Converts JSON data into the domain model.
        public Road toRoad() {
            RoadCondition condition = new RoadCondition(congestionLevel, vehicleDensity, roadStatus);
            return new Road(id, startIntersectionId, endIntersectionId, distance, speedLimit,
                    capacity, currentVehicleCount, condition);
        }
    }
}
