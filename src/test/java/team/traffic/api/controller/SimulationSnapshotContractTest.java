package team.traffic.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.statistics.TrafficStatistics;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.service.StatisticsService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;

/**
 * Pins the JSON contract of GET /api/simulation/snapshot so the frontend can
 * rely on a stable shape: tick, status, vehicles (with route/progress),
 * trafficEvents, roads (dynamic state), and statistics.
 */
class SimulationSnapshotContractTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Javalin app;

    @BeforeEach
    void setUp() {
        SimulationController controller =
                new SimulationController(new FixedSimulationService(), new FixedStatisticsService());
        app = Javalin.create();
        controller.registerRoutes(app);
        app.start(0);
    }

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void snapshotExposesTickStatusVehiclesEventsRoadsAndStatistics() throws Exception {
        JsonNode body = MAPPER.readTree(get("/api/simulation/snapshot").body());

        assertThat(body.get("tick").asLong()).isEqualTo(7);
        assertThat(body.get("status").asText()).isEqualTo("RUNNING");
        assertThat(body.get("vehicles").isArray()).isTrue();
        assertThat(body.get("trafficEvents").isArray()).isTrue();
        assertThat(body.get("roads").isArray()).isTrue();
        assertThat(body.get("statistics").isObject()).isTrue();
    }

    @Test
    void snapshotVehiclesCarryRouteAndProgressFields() throws Exception {
        JsonNode vehicle = MAPPER.readTree(get("/api/simulation/snapshot").body())
                .get("vehicles").get(0);

        assertThat(vehicle.get("id").asText()).isEqualTo("car-1");
        assertThat(vehicle.get("currentIntersectionId").asText()).isEqualTo("A");
        assertThat(vehicle.get("destinationIntersectionId").asText()).isEqualTo("B");
        assertThat(vehicle.get("status").asText()).isNotBlank();
        assertThat(vehicle.has("occupiedRoadId")).isTrue();
        assertThat(vehicle.get("nextRouteStepIndex").isInt()).isTrue();
        assertThat(vehicle.get("travelHistory").isArray()).isTrue();
        assertThat(vehicle.get("routeSteps").isArray()).isTrue();
    }

    @Test
    void snapshotRoadsUseTheSameShapeAsTheNetworkRoadsEndpoint() throws Exception {
        JsonNode road = MAPPER.readTree(get("/api/simulation/snapshot").body())
                .get("roads").get(0);

        assertThat(road.get("id").asText()).isEqualTo("AB");
        assertThat(road.get("startIntersectionId").asText()).isEqualTo("A");
        assertThat(road.get("endIntersectionId").asText()).isEqualTo("B");
        assertThat(road.get("distance").isNumber()).isTrue();
        assertThat(road.get("speedLimit").isNumber()).isTrue();
        assertThat(road.get("capacity").isInt()).isTrue();
        assertThat(road.get("currentVehicleCount").isInt()).isTrue();
        assertThat(road.get("status").asText()).isNotBlank();
        assertThat(road.get("congestionLevel").isNumber()).isTrue();
    }

    @Test
    void snapshotStatisticsMatchTheStatisticsTrafficEndpointShape() throws Exception {
        JsonNode stats = MAPPER.readTree(get("/api/simulation/snapshot").body())
                .get("statistics");

        assertThat(stats.get("currentTick").asLong()).isEqualTo(7);
        assertThat(stats.get("totalVehicles").asInt()).isEqualTo(1);
        assertThat(stats.get("averageCongestionLevel").isNumber()).isTrue();
        assertThat(stats.has("arrivedVehicles")).isTrue();
        assertThat(stats.has("movingVehicles")).isTrue();
        assertThat(stats.has("blockedVehicles")).isTrue();
        assertThat(stats.has("waitingVehicles")).isTrue();
        assertThat(stats.has("congestedRoadCount")).isTrue();
        assertThat(stats.has("blockedRoadCount")).isTrue();
        assertThat(stats.has("closedRoadCount")).isTrue();
        assertThat(stats.has("activeEventCount")).isTrue();
    }

    @Test
    void tickEndpointReturnsTheSameSnapshotContract() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + "/api/simulation/tick"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        HttpResponse<String> response =
                HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = MAPPER.readTree(response.body());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(body.has("tick")).isTrue();
        assertThat(body.has("status")).isTrue();
        assertThat(body.has("vehicles")).isTrue();
        assertThat(body.has("trafficEvents")).isTrue();
        assertThat(body.has("roads")).isTrue();
        assertThat(body.has("statistics")).isTrue();
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class FixedSimulationService implements SimulationService {
        private final List<Vehicle> vehicles = List.of(new Car("car-1", "A", "B"));
        private final List<TrafficEvent> events =
                List.of(new CongestionEvent("ev-1", "AB", 0, 5, 0.4));
        private final List<Road> roads = List.of(new Road(
                "AB", "A", "B", 2.0, 50.0, 10, 3,
                new RoadCondition(0.3, 0.3, RoadStatus.OPEN)));

        @Override public void initialize(List<VehicleConfig> vehicles, List<EventConfig> events) {}
        @Override public void start() {}
        @Override public void pause() {}
        @Override public void resume() {}
        @Override public void reset() {}
        @Override public void advanceTick() {}
        @Override public void addEvent(TrafficEvent event) {}
        @Override public SimulationStatus getStatus() { return SimulationStatus.RUNNING; }
        @Override public long getCurrentTick() { return 7; }

        @Override
        public SimulationSnapshot getSnapshot() {
            return new SimulationSnapshot(getCurrentTick(), getStatus(), vehicles, events, roads);
        }

        @Override public List<Vehicle> getVehicles() { return vehicles; }
        @Override public List<TrafficEvent> getEvents() { return events; }
    }

    private static class FixedStatisticsService implements StatisticsService {
        @Override
        public TrafficStatistics getTrafficStatistics() {
            TrafficStatistics stats = new TrafficStatistics();
            stats.setCurrentTick(7);
            stats.setTotalVehicles(1);
            stats.setAverageCongestionLevel(0.4);
            return stats;
        }

        @Override
        public List<AlgorithmStatistics> getAllAlgorithmStatistics() {
            return List.of();
        }

        @Override
        public java.util.Map<String, Object> getFullStatistics() {
            return java.util.Map.of();
        }
    }
}
