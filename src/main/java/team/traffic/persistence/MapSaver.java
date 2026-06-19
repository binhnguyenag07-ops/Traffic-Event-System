package team.traffic.persistence;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

public class MapSaver {
    private final ObjectMapper objectMapper;

    public MapSaver() {
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    // Saves the current road network as JSON.
    public void save(RoadNetwork roadNetwork, String filePath) throws IOException {
        if (roadNetwork == null) {
            throw new IllegalArgumentException("Road network is required");
        }
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("Map file path is required");
        }

        Path path = Path.of(filePath);
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(path.toFile(), MapData.from(roadNetwork));
    }

    static class MapData {
        public List<IntersectionData> intersections = new ArrayList<>();
        public List<RoadData> roads = new ArrayList<>();

        // Copies domain objects into simple JSON-friendly data objects.
        static MapData from(RoadNetwork roadNetwork) {
            MapData mapData = new MapData();

            List<Intersection> intersections = new ArrayList<>(roadNetwork.getIntersections().values());
            intersections.sort(Comparator.comparing(Intersection::getId));
            for (Intersection intersection : intersections) {
                mapData.intersections.add(IntersectionData.from(intersection));
            }

            List<Road> roads = new ArrayList<>(roadNetwork.getRoads().values());
            roads.sort(Comparator.comparing(Road::getId));
            for (Road road : roads) {
                mapData.roads.add(RoadData.from(road));
            }

            return mapData;
        }
    }

    static class IntersectionData {
        public String id;
        public String name;
        public double x;
        public double y;
        public String locationType;

        static IntersectionData from(Intersection intersection) {
            IntersectionData data = new IntersectionData();
            data.id = intersection.getId();
            data.name = intersection.getName();
            data.x = intersection.getX();
            data.y = intersection.getY();
            data.locationType = intersection.getLocationType().name();
            return data;
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

        static RoadData from(Road road) {
            RoadCondition condition = road.getCondition();
            RoadData data = new RoadData();
            data.id = road.getId();
            data.startIntersectionId = road.getStartIntersectionId();
            data.endIntersectionId = road.getEndIntersectionId();
            data.distance = road.getDistance();
            data.speedLimit = road.getSpeedLimit();
            data.capacity = road.getCapacity();
            data.currentVehicleCount = road.getCurrentVehicleCount();
            data.congestionLevel = condition.getCongestionLevel();
            data.vehicleDensity = condition.getVehicleDensity();
            data.roadStatus = condition.getRoadStatus();
            return data;
        }
    }
}
