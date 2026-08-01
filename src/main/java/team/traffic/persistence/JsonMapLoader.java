package team.traffic.persistence;

import java.io.IOException;
import java.nio.file.Path;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.LandCover;
import team.traffic.domain.network.LandCoverType;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BusStop;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
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

        return buildNetwork(mapData);
    }

    public RoadNetwork load(InputStream input) throws IOException {
        if (input == null) {
            throw new InvalidMapException("JSON map input is required");
        }
        MapData mapData;
        try {
            mapData = objectMapper.readValue(input, MapData.class);
        } catch (JsonProcessingException exception) {
            throw new InvalidMapException("Invalid JSON map: " + exception.getOriginalMessage(), exception);
        }
        return buildNetwork(mapData);
    }

    private RoadNetwork buildNetwork(MapData mapData) throws InvalidMapException {
        if (mapData == null) {
            throw new InvalidMapException("JSON map root must be an object");
        }
        if (mapData.intersections == null || mapData.intersections.isEmpty()) {
            throw new InvalidMapException("Map must contain at least one intersection");
        }

        RoadNetwork roadNetwork = new RoadNetwork();
        addIntersections(roadNetwork, mapData.intersections);
        addRoads(roadNetwork, mapData.roads == null ? Collections.emptyList() : mapData.roads);
        addTurnaroundPoints(
                roadNetwork,
                mapData.turnaroundPoints == null
                        ? Collections.emptyList()
                        : mapData.turnaroundPoints);
        addJunctionMovements(
                roadNetwork,
                mapData.junctionMovements == null
                        ? Collections.emptyList()
                        : mapData.junctionMovements);
        addBuildings(roadNetwork, mapData.buildings == null ? Collections.emptyList() : mapData.buildings);
        addLandCovers(roadNetwork, mapData.landCovers == null ? Collections.emptyList() : mapData.landCovers);
        addBusStops(roadNetwork, mapData.busStops == null ? Collections.emptyList() : mapData.busStops);
        return roadNetwork;
    }

    private void addTurnaroundPoints(
            RoadNetwork roadNetwork,
            List<TurnaroundPointData> points) throws InvalidMapException {
        TurnaroundPointNormalizer normalizer = new TurnaroundPointNormalizer();
        for (int index = 0; index < points.size(); index++) {
            try {
                TurnaroundPointData data =
                        requireEntry(points.get(index), "turnaroundPoint", index);
                normalizer.normalize(
                        roadNetwork,
                        data.id,
                        data.physicalRoadId,
                        data.referenceRoadId,
                        requireField(
                                data.fractionAlongReferenceRoad,
                                "fractionAlongReferenceRoad"),
                        data.allowedVehicleTypes,
                        data.penaltySeconds == null
                                ? team.traffic.domain.network.JunctionMovementGraph
                                        .DEFAULT_UTURN_PENALTY_SECONDS
                                : data.penaltySeconds);
            } catch (RuntimeException exception) {
                throw invalidEntry("turnaroundPoint", index, exception);
            }
        }
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

    private void addJunctionMovements(
            RoadNetwork roadNetwork,
            List<JunctionMovementData> movements) throws InvalidMapException {
        for (int index = 0; index < movements.size(); index++) {
            try {
                JunctionMovementData data =
                        requireEntry(movements.get(index), "junctionMovement", index);
                JunctionMovement movement = data.toMovement();
                validateMovementReferences(roadNetwork, movement);
                roadNetwork.getMovementGraph().register(movement);
            } catch (RuntimeException exception) {
                throw invalidEntry("junctionMovement", index, exception);
            }
        }
    }

    private static void validateMovementReferences(
            RoadNetwork roadNetwork,
            JunctionMovement movement) {
        if (!roadNetwork.containsIntersection(movement.junctionId())) {
            throw new IllegalArgumentException(
                    "Junction does not exist: " + movement.junctionId());
        }
        Road target = roadNetwork.getRoad(movement.toRoadId());
        if (target == null
                || !movement.junctionId().equals(target.getStartIntersectionId())) {
            throw new IllegalArgumentException(
                    "Target road must leave the movement junction: "
                            + movement.toRoadId());
        }
        movement.fromRoadId().ifPresent(fromRoadId -> {
            Road source = roadNetwork.getRoad(fromRoadId);
            if (source == null
                    || !movement.junctionId().equals(source.getEndIntersectionId())) {
                throw new IllegalArgumentException(
                        "Source road must enter the movement junction: " + fromRoadId);
            }
        });
    }

    private void addBuildings(RoadNetwork roadNetwork, List<BuildingData> buildings)
            throws InvalidMapException {
        for (int index = 0; index < buildings.size(); index++) {
            try {
                BuildingData data = requireEntry(buildings.get(index), "building", index);
                roadNetwork.addBuilding(data.toBuilding());
            } catch (RuntimeException exception) {
                throw invalidEntry("building", index, exception);
            }
        }
    }

    private void addBusStops(RoadNetwork roadNetwork, List<BusStopData> busStops)
            throws InvalidMapException {
        for (int index = 0; index < busStops.size(); index++) {
            try {
                BusStopData data = requireEntry(busStops.get(index), "busStop", index);
                roadNetwork.addBusStop(data.toBusStop());
            } catch (RuntimeException exception) {
                throw invalidEntry("busStop", index, exception);
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
        public List<TurnaroundPointData> turnaroundPoints;
        public List<JunctionMovementData> junctionMovements;
        public List<BuildingData> buildings;
        public List<LandCoverData> landCovers;
        public List<BusStopData> busStops;
    }

    static class BusStopData {
        public String id;
        public String name;
        public String roadId;
        public Double positionAlongRoad;
        public Double dwellSeconds;

        BusStop toBusStop() {
            if (positionAlongRoad == null) {
                throw new IllegalArgumentException("Bus-stop positionAlongRoad is required.");
            }
            // Dwell is optional in the map: the generator knows where a stop is
            // but not how long a bus waits there, which is a property of the
            // service rather than of the kerb.
            double dwell = dwellSeconds == null
                    ? BusStop.DEFAULT_DWELL_SECONDS
                    : dwellSeconds;
            return new BusStop(id, name, roadId, positionAlongRoad, dwell);
        }
    }

    static class TurnaroundPointData {
        public String id;
        public String physicalRoadId;
        public String referenceRoadId;
        public Double fractionAlongReferenceRoad;
        public Set<VehicleType> allowedVehicleTypes;
        public Double penaltySeconds;
    }

    static class JunctionMovementData {
        public String junctionId;
        public String fromRoadId;
        public String toRoadId;
        public TurnDirection direction;
        public MovementPermission permission;
        public Set<VehicleType> allowedVehicleTypes;
        public Double penaltySeconds;
        public MovementProvenance provenance;

        JunctionMovement toMovement() {
            return new JunctionMovement(
                    junctionId,
                    Optional.ofNullable(fromRoadId),
                    toRoadId,
                    requireField(direction, "direction"),
                    requireField(permission, "permission"),
                    allowedVehicleTypes == null ? Set.of() : allowedVehicleTypes,
                    penaltySeconds == null ? 0.0 : penaltySeconds,
                    provenance == null ? MovementProvenance.EXPLICIT : provenance);
        }
    }

    static class BuildingData {
        public String id;
        public String name;
        public BuildingType type;
        public Double x;
        public Double y;
        public Double latitude;
        public Double longitude;
        public List<BuildingAccessData> accessPoints;
        public List<List<Double>> footprint;

        public Building toBuilding() {
            if (accessPoints == null) throw new IllegalArgumentException("Building accessPoints are required");
            return new Building(
                    id,
                    name,
                    requireField(type, "type"),
                    requireField(x, "x"),
                    requireField(y, "y"),
                    latitude,
                    longitude,
                    accessPoints.stream().map(BuildingAccessData::toAccessPoint).toList(),
                    toFootprint(footprint));
        }

        /** Optional outline; a map that carries only a position still loads. */
        private static List<GeoPoint> toFootprint(List<List<Double>> coordinates) {
            if (coordinates == null) return List.of();
            return coordinates.stream().map(point -> {
                if (point == null || point.size() < 2) {
                    throw new IllegalArgumentException("Building footprint points require latitude and longitude");
                }
                return new GeoPoint(point.get(0), point.get(1));
            }).toList();
        }
    }

    static class BuildingAccessData {
        public String id;
        public String intersectionId;
        public Double drivewayDistanceMetres;

        public BuildingAccessPoint toAccessPoint() {
            return new BuildingAccessPoint(
                    id,
                    intersectionId,
                    drivewayDistanceMetres == null ? 0.0 : drivewayDistanceMetres);
        }
    }

    static class IntersectionData {
        public String id;
        public String name;
        public Double x;
        public Double y;
        /** Accepted only so pre-migration map files remain readable. */
        public String locationType;
        public Double latitude;
        public Double longitude;

        // Converts JSON data into the domain model.
        public Intersection toIntersection() {
            return new Intersection(
                    id,
                    name,
                    requireField(x, "x"),
                    requireField(y, "y"),
                    latitude,
                    longitude);
        }
    }

    static class RoadData {
        public String id;
        public String name;
        public String physicalRoadId;
        public String startIntersectionId;
        public String endIntersectionId;
        public Double distance;
        public Double speedLimit;
        public Integer capacity;
        public Integer currentVehicleCount;
        public Integer laneCount;
        public Double laneWidthMetres;
        public Double congestionLevel;
        public Double vehicleDensity;
        public RoadStatus roadStatus;
        public RoadClass roadClass;
        public List<List<Double>> geometry;

        // Converts JSON data into the domain model.
        public Road toRoad() {
            int requiredCapacity = requireField(capacity, "capacity");
            int requiredVehicleCount = requireField(currentVehicleCount, "currentVehicleCount");
            double requiredVehicleDensity = requireField(vehicleDensity, "vehicleDensity");
            RoadDensityValidator.validate(id, requiredCapacity, requiredVehicleCount, requiredVehicleDensity);

            RoadCondition condition = new RoadCondition(
                    requireField(congestionLevel, "congestionLevel"),
                    requiredVehicleDensity,
                    requireField(roadStatus, "roadStatus"));
            return new Road(
                    id,
                    defaultIfBlank(name, id),
                    defaultIfBlank(physicalRoadId, defaultPhysicalRoadId(startIntersectionId, endIntersectionId)),
                    startIntersectionId,
                    endIntersectionId,
                    requireField(distance, "distance"),
                    requireField(speedLimit, "speedLimit"),
                    requiredCapacity,
                    requiredVehicleCount,
                    laneCount == null
                            ? normalizedRoadClass().getDefaultLaneCount()
                            : laneCount,
                    laneWidthMetres == null
                            ? normalizedRoadClass().getDefaultLaneWidthMetres()
                            : laneWidthMetres,
                    condition,
                    toGeometry(geometry),
                    normalizedRoadClass());
        }

        private RoadClass normalizedRoadClass() {
            return roadClass == null ? RoadClass.UNCLASSIFIED : roadClass;
        }

        private List<GeoPoint> toGeometry(List<List<Double>> coordinates) {
            if (coordinates == null) {
                return List.of();
            }
            return coordinates.stream().map(coordinate -> {
                if (coordinate == null || coordinate.size() != 2
                        || coordinate.get(0) == null || coordinate.get(1) == null) {
                    throw new IllegalArgumentException("Road geometry points require latitude and longitude");
                }
                return new GeoPoint(coordinate.get(0), coordinate.get(1));
            }).toList();
        }
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String defaultPhysicalRoadId(String firstIntersectionId, String secondIntersectionId) {
        if (firstIntersectionId == null || secondIntersectionId == null) {
            return "legacy-road";
        }
        return firstIntersectionId.compareTo(secondIntersectionId) <= 0
                ? firstIntersectionId + "<->" + secondIntersectionId
                : secondIntersectionId + "<->" + firstIntersectionId;
    }

    private static <T> T requireField(T value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException("Required field is missing: " + fieldName);
        }
        return value;
    }

    /** Land cover is optional; a map without it simply has no parks or water drawn. */
    private void addLandCovers(RoadNetwork roadNetwork, List<LandCoverData> covers) {
        for (int index = 0; index < covers.size(); index++) {
            try {
                LandCoverData data = requireEntry(covers.get(index), "landCover", index);
                roadNetwork.addLandCover(data.toLandCover());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid land cover at index " + index + ": " + exception.getMessage(), exception);
            }
        }
    }

    static class LandCoverData {
        public String id;
        public LandCoverType type;
        public List<List<Double>> polygon;

        public LandCover toLandCover() {
            if (polygon == null) throw new IllegalArgumentException("Land cover polygon is required");
            return new LandCover(id, requireField(type, "type"), polygon.stream().map(point -> {
                if (point == null || point.size() < 2) {
                    throw new IllegalArgumentException("Land cover points require latitude and longitude");
                }
                return new GeoPoint(point.get(0), point.get(1));
            }).toList());
        }
    }
}
