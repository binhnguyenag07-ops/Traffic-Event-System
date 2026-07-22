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
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;
import team.traffic.simulation.trace.VehicleTracePoint;
import team.traffic.simulation.trace.VehicleTraceSummary;

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

    @Test
    void traceEndpointsExposeTheSummaryARangeAndASingleState() throws Exception {
        HttpResponse<String> summaryResponse = get("/api/simulation/trace");
        HttpResponse<String> rangeResponse = get("/api/simulation/trace/vehicles/car-1?fromTick=1&toTick=3");
        HttpResponse<String> exactResponse = get("/api/simulation/trace/vehicles/car-1/ticks/3");
        HttpResponse<String> tickResponse = get("/api/simulation/trace/ticks/3");

        assertThat(summaryResponse.statusCode()).isEqualTo(200);
        assertThat(summaryResponse.body()).contains("\"recordedTickCount\":4");
        assertThat(summaryResponse.body()).contains("\"vehicleIds\":[\"car-1\"]");
        assertThat(rangeResponse.statusCode()).isEqualTo(200);
        assertThat(rangeResponse.body()).contains("\"vehicleId\":\"car-1\"");
        assertThat(rangeResponse.body()).contains("\"tick\":3");
        assertThat(simulationService.capturedFromTick).isEqualTo(1L);
        assertThat(simulationService.capturedToTick).isEqualTo(3L);
        assertThat(exactResponse.statusCode()).isEqualTo(200);
        assertThat(exactResponse.body()).contains("\"currentRoadId\":\"AB\"");
        assertThat(tickResponse.statusCode()).isEqualTo(200);
        assertThat(tickResponse.body()).contains("\"roadProgress\":0.5");
    }

    @Test
    void aVehicleTraceRangeDefaultsToTheWholeRunWhenNoTicksAreGiven() throws Exception {
        HttpResponse<String> response = get("/api/simulation/trace/vehicles/car-1");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(simulationService.capturedFromTick).isZero();
        assertThat(simulationService.capturedToTick).isEqualTo(3L);
    }

    @Test
    void traceEndpointsValidateTicksAndReturnNotFoundForMissingState() throws Exception {
        HttpResponse<String> invalidRange = get("/api/simulation/trace/vehicles/car-1?fromTick=4&toTick=2");
        HttpResponse<String> negativeTick = get("/api/simulation/trace/vehicles/car-1?fromTick=-1");
        HttpResponse<String> missingTick = get("/api/simulation/trace/ticks/99");
        HttpResponse<String> missingVehicle = get("/api/simulation/trace/vehicles/missing/ticks/3");

        assertThat(invalidRange.statusCode()).isEqualTo(400);
        assertThat(invalidRange.body()).contains("fromTick");
        assertThat(negativeTick.statusCode()).isEqualTo(400);
        assertThat(missingTick.statusCode()).isEqualTo(404);
        assertThat(missingVehicle.statusCode()).isEqualTo(404);
    }

    @Test
    void signalAndFlowDiagnosticEndpointsAreAvailable() throws Exception {
        HttpResponse<String> signals = get("/api/simulation/signals");
        HttpResponse<String> flow = get("/api/simulation/trace/flow");
        HttpResponse<String> fundamental = get("/api/simulation/trace/fundamental");
        HttpResponse<String> timeSpace = get("/api/simulation/trace/roads/AB/time-space");

        assertThat(signals.statusCode()).isEqualTo(200);
        assertThat(flow.statusCode()).isEqualTo(200);
        assertThat(fundamental.statusCode()).isEqualTo(200);
        assertThat(timeSpace.statusCode()).isEqualTo(200);
        assertThat(timeSpace.body()).contains("\"roadId\":\"AB\"", "\"points\":[]");
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
        private long capturedFromTick;
        private long capturedToTick;

        private final VehicleTracePoint tracePoint = new VehicleTracePoint(
                3,
                "car-1",
                VehicleType.CAR,
                VehicleStatus.MOVING,
                "A",
                "B",
                "AB",
                "A",
                "B",
                0.5,
                30.0,
                24.0,
                1,
                List.of("AB"));

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

        @Override
        public VehicleTraceSummary getVehicleTraceSummary() {
            return new VehicleTraceSummary(0L, 3L, 4, List.of("car-1"));
        }

        @Override
        public boolean hasVehicleTraceAtTick(long tick) {
            return tick == 3;
        }

        @Override
        public List<VehicleTracePoint> getVehicleStatesAtTick(long tick) {
            return tick == 3 ? List.of(tracePoint) : List.of();
        }

        @Override
        public Optional<VehicleTracePoint> getVehicleStateAtTick(String vehicleId, long tick) {
            return "car-1".equals(vehicleId) && tick == 3 ? Optional.of(tracePoint) : Optional.empty();
        }

        @Override
        public List<VehicleTracePoint> getVehicleTrace(String vehicleId, long fromTick, long toTick) {
            capturedFromTick = fromTick;
            capturedToTick = toTick;
            if (fromTick > toTick) {
                throw new IllegalArgumentException("Trace fromTick must not exceed toTick.");
            }
            return "car-1".equals(vehicleId) && fromTick <= 3 && toTick >= 3
                    ? List.of(tracePoint)
                    : List.of();
        }
    }
}
