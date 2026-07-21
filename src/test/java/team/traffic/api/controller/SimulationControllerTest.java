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
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;

class SimulationControllerTest {
    private Javalin app;
    private CapturingSimulationService simulationService;

    @BeforeEach
    void setUp() {
        simulationService = new CapturingSimulationService();
        SimulationController controller = new SimulationController(simulationService);
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
    void initializePreservesEventValueAndCausingVehicleFromPayload() throws Exception {
        HttpResponse<String> response = postJson(
                "/api/simulation/initialize",
                """
                {
                  "vehicles": [],
                  "events": [
                    {
                      "id": "ev-accident",
                      "type": "ACCIDENT",
                      "affectedRoadId": "AB",
                      "startTick": 2,
                      "durationTicks": 7,
                      "value": 0.8,
                      "causingVehicleId": "car-42"
                    }
                  ]
                }
                """);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(simulationService.events).hasSize(1);
        EventConfig event = simulationService.events.get(0);
        assertThat(event.id()).isEqualTo("ev-accident");
        assertThat(event.type()).isEqualTo("ACCIDENT");
        assertThat(event.value()).isEqualTo(0.8);
        assertThat(event.causingVehicleId()).isEqualTo("car-42");
    }

    @Test
    void snapshotEndpointReturnsTypedSimulationSnapshot() throws Exception {
        simulationService.domainVehicles = List.of(new Car("car-1", "A", "B"));
        simulationService.trafficEvents = List.of(new RoadClosureEvent("ev-1", "AB", 0, 5));

        HttpResponse<String> response = get("/api/simulation/snapshot");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"tick\":3");
        assertThat(response.body()).contains("\"status\":\"RUNNING\"");
        assertThat(response.body()).contains("\"vehicles\"");
        assertThat(response.body()).contains("\"trafficEvents\"");
        assertThat(response.body()).contains("\"car-1\"");
        assertThat(response.body()).contains("\"ev-1\"");
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

    private static class CapturingSimulationService implements SimulationService {
        private List<VehicleConfig> vehicles = new ArrayList<>();
        private List<EventConfig> events = new ArrayList<>();
        private List<Vehicle> domainVehicles = List.of();
        private List<TrafficEvent> trafficEvents = List.of();

        @Override
        public void initialize(List<VehicleConfig> vehicles, List<EventConfig> events) {
            this.vehicles = List.copyOf(vehicles);
            this.events = List.copyOf(events);
        }

        @Override public void start() {}
        @Override public void pause() {}
        @Override public void resume() {}
        @Override public void reset() {}
        @Override public void advanceTick() {}
        @Override public void addEvent(TrafficEvent event) {}
        @Override public SimulationStatus getStatus() { return SimulationStatus.RUNNING; }
        @Override public long getCurrentTick() { return 3; }

        @Override
        public SimulationSnapshot getSnapshot() {
            return new SimulationSnapshot(getCurrentTick(), getStatus(), domainVehicles, trafficEvents);
        }

        @Override public List<Vehicle> getVehicles() { return domainVehicles; }
        @Override public List<TrafficEvent> getEvents() { return trafficEvents; }
    }
}
