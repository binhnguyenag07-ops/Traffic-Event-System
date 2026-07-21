package team.traffic.service.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.exception.InvalidMapException;
import team.traffic.persistence.JsonMapLoader;
import team.traffic.service.RoadGeometryProvider;

public class MapImportService {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_INTERSECTIONS = 600;
    public static final int MAX_ROADS = 1_200;
    public static final double SOUTH = 10.762;
    public static final double WEST = 106.687;
    public static final double NORTH = 10.789;
    public static final double EAST = 106.718;

    private final JsonMapLoader loader;
    private final RoadGeometryProvider geometryProvider;

    public MapImportService(RoadGeometryProvider geometryProvider) {
        this.loader = new JsonMapLoader();
        this.geometryProvider = geometryProvider;
    }

    public ImportResult importJson(String filename, InputStream input) throws IOException {
        if (filename == null || !filename.toLowerCase().endsWith(".json")) {
            throw new InvalidMapException("Map upload must be a .json file");
        }
        byte[] bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        if (bytes.length > MAX_FILE_BYTES) {
            throw new InvalidMapException("Map upload must not exceed 2 MB");
        }
        RoadNetwork loaded = loader.load(new ByteArrayInputStream(bytes));
        validateSize(loaded);
        validateCoordinates(loaded);
        return enrichGeometry(loaded);
    }

    private void validateSize(RoadNetwork network) throws InvalidMapException {
        if (network.getIntersectionCount() > MAX_INTERSECTIONS) {
            throw new InvalidMapException("Map must not contain more than " + MAX_INTERSECTIONS + " intersections");
        }
        if (network.getRoadCount() > MAX_ROADS) {
            throw new InvalidMapException("Map must not contain more than " + MAX_ROADS + " roads");
        }
    }

    private void validateCoordinates(RoadNetwork network) throws InvalidMapException {
        for (Intersection intersection : network.getIntersections().values()) {
            Double latitude = intersection.getLatitude();
            Double longitude = intersection.getLongitude();
            if (latitude == null || longitude == null) {
                throw new InvalidMapException("Intersection " + intersection.getId()
                        + " requires latitude and longitude for real-map import");
            }
            if (latitude < SOUTH || latitude > NORTH || longitude < WEST || longitude > EAST) {
                throw new InvalidMapException("Intersection " + intersection.getId()
                        + " is outside the HCMC operating area");
            }
        }
    }

    private ImportResult enrichGeometry(RoadNetwork loaded) {
        RoadNetwork result = new RoadNetwork();
        loaded.getIntersections().values().forEach(result::addIntersection);
        Map<String, Integer> sources = new LinkedHashMap<>();
        sources.put("FILE", 0);
        sources.put("OSRM", 0);
        sources.put("STRAIGHT", 0);

        for (Road road : loaded.getRoads().values()) {
            List<GeoPoint> geometry = road.getGeometry();
            double distance = road.getDistance();
            String source = "FILE";
            if (geometry.size() < 2) {
                RoadGeometryProvider.RoadGeometryResult routed = geometryProvider.route(
                        loaded.getIntersection(road.getStartIntersectionId()),
                        loaded.getIntersection(road.getEndIntersectionId()));
                geometry = routed.geometry();
                distance = routed.distanceKilometres();
                source = routed.source().toUpperCase();
            }
            sources.compute(source, (ignored, count) -> count == null ? 1 : count + 1);
            RoadCondition condition = road.getCondition();
            result.addRoad(new Road(
                    road.getId(), road.getStartIntersectionId(), road.getEndIntersectionId(),
                    distance, road.getSpeedLimit(), road.getCapacity(), road.getCurrentVehicleCount(),
                    new RoadCondition(condition.getCongestionLevel(), condition.getVehicleDensity(),
                            condition.getRoadStatus()), geometry, road.getRoadClass()));
        }
        Bounds bounds = bounds(result);
        return new ImportResult(result, Map.copyOf(sources), bounds);
    }

    private Bounds bounds(RoadNetwork network) {
        double south = Double.POSITIVE_INFINITY;
        double west = Double.POSITIVE_INFINITY;
        double north = Double.NEGATIVE_INFINITY;
        double east = Double.NEGATIVE_INFINITY;
        for (Intersection intersection : network.getIntersections().values()) {
            south = Math.min(south, intersection.getLatitude());
            west = Math.min(west, intersection.getLongitude());
            north = Math.max(north, intersection.getLatitude());
            east = Math.max(east, intersection.getLongitude());
        }
        return new Bounds(south, west, north, east);
    }

    public record Bounds(double south, double west, double north, double east) {}

    public record ImportResult(RoadNetwork network, Map<String, Integer> geometrySources, Bounds bounds) {}
}
