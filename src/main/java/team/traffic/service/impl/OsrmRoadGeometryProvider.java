package team.traffic.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.service.RoadGeometryProvider;

public class OsrmRoadGeometryProvider implements RoadGeometryProvider {
    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public OsrmRoadGeometryProvider() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build(),
                new ObjectMapper(), "https://router.project-osrm.org");
    }

    OsrmRoadGeometryProvider(HttpClient client, ObjectMapper objectMapper, String baseUrl) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    @Override
    public RoadGeometryResult route(Intersection start, Intersection end) {
        requireCoordinates(start);
        requireCoordinates(end);
        try {
            String coordinates = start.getLongitude() + "," + start.getLatitude()
                    + ";" + end.getLongitude() + "," + end.getLatitude();
            URI uri = URI.create(baseUrl + "/route/v1/driving/" + coordinates
                    + "?overview=simplified&geometries=geojson");
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(4))
                    .header("User-Agent", "Traffic-Event-System student project").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) return fallback(start, end);
            JsonNode route = objectMapper.readTree(response.body()).path("routes").path(0);
            JsonNode coordinatesNode = route.path("geometry").path("coordinates");
            if (!coordinatesNode.isArray() || coordinatesNode.size() < 2) return fallback(start, end);
            List<GeoPoint> geometry = new ArrayList<>();
            for (JsonNode point : coordinatesNode) {
                geometry.add(new GeoPoint(point.get(1).asDouble(), point.get(0).asDouble()));
            }
            double distance = route.path("distance").asDouble() / 1000.0;
            return !Double.isFinite(distance) || distance <= 0
                    ? fallback(start, end)
                    : new RoadGeometryResult(geometry, distance, "OSRM");
        } catch (Exception exception) {
            return fallback(start, end);
        }
    }

    private RoadGeometryResult fallback(Intersection start, Intersection end) {
        return new RoadGeometryResult(List.of(
                new GeoPoint(start.getLatitude(), start.getLongitude()),
                new GeoPoint(end.getLatitude(), end.getLongitude())), haversine(start, end), "STRAIGHT");
    }

    private double haversine(Intersection start, Intersection end) {
        double lat1 = Math.toRadians(start.getLatitude());
        double lat2 = Math.toRadians(end.getLatitude());
        double deltaLat = lat2 - lat1;
        double deltaLon = Math.toRadians(end.getLongitude() - start.getLongitude());
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        return Math.max(0.001, 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
    }

    private void requireCoordinates(Intersection intersection) {
        if (intersection == null || intersection.getLatitude() == null || intersection.getLongitude() == null) {
            throw new IllegalArgumentException("Road endpoints require geographic coordinates");
        }
    }
}
