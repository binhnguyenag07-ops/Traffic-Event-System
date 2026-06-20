package team.traffic.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.exception.InvalidMapException;

public class JsonMapLoader implements MapLoader {
    private final ObjectMapper objectMapper;

    public JsonMapLoader() {
        this.objectMapper = new ObjectMapper();
    }

    // Reads intersections first, then roads, so road endpoints can be validated.
    @Override
    public RoadNetwork load(String filePath) throws IOException {
        Path path = requireFilePath(filePath);
        MapData mapData;
        try {
            mapData = objectMapper.readValue(path.toFile(), MapData.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidMapException("Invalid JSON map: " + exception.getOriginalMessage(), exception);
        }

        if (mapData == null) {
            throw new InvalidMapException("JSON map root must be an object");
        }
        if (mapData.intersections == null || mapData.intersections.isEmpty()) {
            throw new InvalidMapException("Map must contain at least one intersection");
        }

        RoadNetwork roadNetwork = new RoadNetwork();
        addIntersections(roadNetwork, mapData.intersections);
        addRoads(roadNetwork, mapData.roads == null ? Collections.emptyList() : mapData.roads);
        return roadNetwork;
    }

    private Path requireFilePath(String filePath) throws InvalidMapException {
        if (filePath == null || filePath.isBlank()) {
            throw new InvalidMapException("Map file path is required");
        }
        return Path.of(filePath);
    }

    private void addIntersections(RoadNetwork roadNetwork, List<IntersectionData> intersections)
            throws InvalidMapException {
        for (int index = 0; index < intersections.size(); index++) {
            try {
                IntersectionData data = requireEntry(intersections.get(index), "intersection", index);
                roadNetwork.addIntersection(data.toIntersection());
            } catch (RuntimeException exception) {
                throw invalidEntry("intersection", index, exception);
            }
        }
    }

    private void addRoads(RoadNetwork roadNetwork, List<RoadData> roads)
            throws InvalidMapException {
        for (int index = 0; index < roads.size(); index++) {
            try {
                RoadData data = requireEntry(roads.get(index), "road", index);
                roadNetwork.addRoad(data.toRoad());
            } catch (RuntimeException exception) {
                throw invalidEntry("road", index, exception);
            }
        }
    }

    private <T> T requireEntry(T entry, String type, int index) {
        if (entry == null) {
            throw new IllegalArgumentException(type + " entry must not be null at index " + index);
        }
        return entry;
    }

    private InvalidMapException invalidEntry(String type, int index, RuntimeException cause) {
        return new InvalidMapException(
                "Invalid JSON " + type + " at index " + index + ": " + cause.getMessage(),
                cause);
    }

    static class MapData {
        public List<IntersectionData> intersections;
        public List<RoadData> roads;
    }

    static class IntersectionData {
        public String id;
        public String name;
        public Double x;
        public Double y;
        public LocationType locationType;

        // Converts JSON data into the domain model.
        public Intersection toIntersection() {
            return new Intersection(
                    id,
                    name,
                    requireField(x, "x"),
                    requireField(y, "y"),
                    locationType);
        }
    }

    static class RoadData {
        public String id;
        public String startIntersectionId;
        public String endIntersectionId;
        public Double distance;
        public Double speedLimit;
        public Integer capacity;
        public Integer currentVehicleCount;
        public Double congestionLevel;
        public Double vehicleDensity;
        public RoadStatus roadStatus;

        // Converts JSON data into the domain model.
        public Road toRoad() {
            RoadCondition condition = new RoadCondition(
                    requireField(congestionLevel, "congestionLevel"),
                    requireField(vehicleDensity, "vehicleDensity"),
                    requireField(roadStatus, "roadStatus"));
            return new Road(
                    id,
                    startIntersectionId,
                    endIntersectionId,
                    requireField(distance, "distance"),
                    requireField(speedLimit, "speedLimit"),
                    requireField(capacity, "capacity"),
                    requireField(currentVehicleCount, "currentVehicleCount"),
                    condition);
        }
    }

    private static <T> T requireField(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Required field is missing: " + fieldName);
        }
        return value;
    }
}
