package team.traffic.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.javalin.Javalin;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.observer.TrafficEventBus;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;
import team.traffic.service.TrafficEventService;
import team.traffic.service.impl.TrafficEventServiceImpl;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;

class TrafficEventControllerTest {
    private Javalin app;
    private TrafficEventService trafficEventService;

    @BeforeEach
    void setUp() {
        RoadNetwork network = createNetwork();
        StubRoadNetworkService roadNetworkService = new StubRoadNetworkService(network);
        trafficEventService = new TrafficEventServiceImpl(new TrafficEventBus(), roadNetworkService);
        SimulationService simulationService = new StubSimulationService();
        TrafficEventController controller = new TrafficEventController(trafficEventService, simulationService);
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

    // --- GET /api/events ---

    @Test
    void getEventsReturnsEmptyListInitially() throws Exception {
        HttpResponse<String> response = get("/api/events");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("[]");
    }

    @Test
    void getEventsReturnsCreatedEvents() throws Exception {
        postJson("/api/events", """
                {
                  "type": "ROAD_CLOSURE",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 5
                }
                """);

        HttpResponse<String> response = get("/api/events");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("ROAD_CLOSURE");
        assertThat(response.body()).contains("road-1");
    }

    // --- POST /api/events ---

    @Test
    void createCongestionEventReturns201() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "CONGESTION",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 10,
                  "value": 0.7
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("CONGESTION");
        assertThat(response.body()).contains("road-1");
        assertThat(response.body()).contains("SCHEDULED");
    }

    @Test
    void createAccidentEventReturns201() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "ACCIDENT",
                  "affectedRoadId": "road-1",
                  "startTick": 5,
                  "durationTicks": 10,
                  "value": 0.8,
                  "causingVehicleId": "car-42"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("ACCIDENT");
        assertThat(response.body()).contains("road-1");
    }

    @Test
    void createRoadClosureEventReturns201() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "ROAD_CLOSURE",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("ROAD_CLOSURE");
    }

    @Test
    void createPositionedBreakdownUsesSecondsAndReturnsAffectedVehicle() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "BREAKDOWN",
                  "affectedRoadId": "road-1",
                  "startTimeSeconds": 12.5,
                  "durationSeconds": 30,
                  "affectedVehicleId": "car-1",
                  "positionAlongRoad": 0.75,
                  "blockedLaneIndexes": [1]
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.body()).contains("BREAKDOWN", "affectedVehicleId", "car-1");
        assertThat(response.body()).contains("\"startTimeSeconds\":12.5");
        assertThat(response.body()).doesNotContain("startTick", "durationTicks");
    }

    @Test
    void createBreakdownRejectsMissingAffectedVehicle() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "BREAKDOWN",
                  "affectedRoadId": "road-1",
                  "startTimeSeconds": 0,
                  "durationSeconds": 30
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("affected vehicle id is required");
    }

    @Test
    void createEventWithDefaultsWhenValueOmitted() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "CONGESTION",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 10
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(trafficEventService.getAllEvents()).hasSize(1);
    }

    @Test
    void createAccidentWithDefaultCausingVehicle() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "ACCIDENT",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 10,
                  "value": 0.5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
    }

    // --- Validation ---

    @Test
    void createEventReturns400WhenTypeIsMissing() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("type is required");
    }

    @Test
    void createEventReturns400WhenAffectedRoadIdIsMissing() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "CONGESTION",
                  "startTick": 0,
                  "durationTicks": 5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("affectedRoadId is required");
    }

    @Test
    void createEventReturns400WhenTypeIsInvalid() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "INVALID_TYPE",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid type");
    }

    @Test
    void createEventReturns400ForInvalidRequestBody() throws Exception {
        HttpResponse<String> response = postJson("/api/events", "not json");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid request body");
    }

    @Test
    void createEventReturns400ForInvalidEventParameters() throws Exception {
        // Negative startTick triggers IllegalArgumentException in TrafficEvent constructor
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "CONGESTION",
                  "affectedRoadId": "road-1",
                  "startTick": -1,
                  "durationTicks": 5,
                  "value": 0.5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid event parameters");
    }

    @Test
    void createEventReturns400ForNegativeDuration() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "ROAD_CLOSURE",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": -5
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Invalid event parameters");
    }

    @Test
    void createEventIsCaseInsensitiveOnType() throws Exception {
        HttpResponse<String> response = postJson("/api/events", """
                {
                  "type": "congestion",
                  "affectedRoadId": "road-1",
                  "startTick": 0,
                  "durationTicks": 5,
                  "value": 0.3
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
    }

    @Test
    void createdEventsAppearInGetAll() throws Exception {
        postJson("/api/events", """
                {
                  "type": "CONGESTION",
                  "affectedRoadId": "road-A",
                  "startTick": 0,
                  "durationTicks": 5,
                  "value": 0.3
                }
                """);
        postJson("/api/events", """
                {
                  "type": "ROAD_CLOSURE",
                  "affectedRoadId": "road-B",
                  "startTick": 0,
                  "durationTicks": 5
                }
                """);

        HttpResponse<String> response = get("/api/events");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("road-A");
        assertThat(response.body()).contains("road-B");
    }

    // --- Helpers ---

    private RoadNetwork createNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        return network;
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static class StubRoadNetworkService implements RoadNetworkService {
        private final RoadNetwork network;

        StubRoadNetworkService(RoadNetwork network) {
            this.network = network;
        }

        @Override
        public RoadNetwork getNetwork() { return network; }

        @Override
        public Road recordVehicleEnteringRoad(String roadId) { throw new UnsupportedOperationException(); }

        @Override
        public Road recordVehicleLeavingRoad(String roadId) { throw new UnsupportedOperationException(); }

        @Override
        public double updateVehicleDensity(String roadId) { throw new UnsupportedOperationException(); }

        @Override
        public double getAverageVehicleDensity() { throw new UnsupportedOperationException(); }

        @Override
        public List<Road> findRoadsAtOrAboveDensity(double minimumDensity) { throw new UnsupportedOperationException(); }
    }

    private static class StubSimulationService implements SimulationService {
        @Override public void initialize(List<VehicleConfig> vehicles, List<EventConfig> events) {}
        @Override public void start() {}
        @Override public void pause() {}
        @Override public void resume() {}
        @Override public void reset() {}
        @Override public void advanceTick() {}
        @Override public void addEvent(TrafficEvent event) {}
        @Override public SimulationStatus getStatus() { return SimulationStatus.READY; }
        @Override public long getCurrentTick() { return 0; }
        @Override public SimulationSnapshot getSnapshot() {
            return new SimulationSnapshot(0, SimulationStatus.READY, List.of(), List.of());
        }
        @Override public List<Vehicle> getVehicles() { return List.of(new team.traffic.domain.vehicle.Car("car-1", "A", "B")); }
        @Override public List<TrafficEvent> getEvents() { return List.of(); }
    }
}
