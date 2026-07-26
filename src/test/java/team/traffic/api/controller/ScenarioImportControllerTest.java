package team.traffic.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.demand.DemandScenario;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.SimulationService;
import team.traffic.service.impl.MapImportService;
import team.traffic.service.impl.RoadNetworkServiceImpl;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;

class ScenarioImportControllerTest {
    private Javalin app;
    private RoadNetworkServiceImpl networkService;
    private CapturingSimulationService simulationService;

    @BeforeEach
    void setUp() {
        RoadNetwork original = new RoadNetwork();
        original.addIntersection(new Intersection("ORIGINAL", "Original", 0, 0, LocationType.NORMAL));
        networkService = new RoadNetworkServiceImpl(original);
        simulationService = new CapturingSimulationService(original);
        MapImportService importService = new MapImportService((start, end) -> null);
        ScenarioController controller = new ScenarioController(networkService, simulationService, importService);
        app = Javalin.create();
        controller.registerRoutes(app);
        app.start(0);
    }

    @AfterEach
    void stop() {
        if (app != null) app.stop();
    }

    @Test
    void validImportReplacesBothNetworkReferences() throws Exception {
        HttpResponse<String> response = upload(validMap());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"intersectionCount\":2").contains("\"status\":\"READY\"");
        assertThat(networkService.getNetwork().containsIntersection("A")).isTrue();
        assertThat(simulationService.network.containsIntersection("A")).isTrue();
    }

    @Test
    void invalidImportPreservesCurrentNetwork() throws Exception {
        HttpResponse<String> response = upload(validMap().replace("10.770", "11.770"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(networkService.getNetwork().containsIntersection("ORIGINAL")).isTrue();
        assertThat(simulationService.network.containsIntersection("ORIGINAL")).isTrue();
    }

    @Test
    void activatingTheShippedScenarioInitializesItsDemandAgainstTheLoadedMap() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port()
                        + "/api/scenarios/district-1/activate"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"buildingCount\":1162")
                .contains("\"scheduledTrips\"");
        assertThat(simulationService.demandScenario).isNotNull();
        assertThat(simulationService.network.getBuildingCount()).isEqualTo(1162);
    }

    private HttpResponse<String> upload(String json) throws Exception {
        String boundary = "TrafficBoundary42";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"map\"; filename=\"district.json\"\r\n"
                + "Content-Type: application/json\r\n\r\n"
                + json + "\r\n--" + boundary + "--\r\n";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + "/api/scenarios/import"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.getBytes(StandardCharsets.UTF_8)))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String validMap() {
        return """
                {"intersections":[
                  {"id":"A","name":"A","x":0,"y":0,"locationType":"NORMAL","latitude":10.770,"longitude":106.700},
                  {"id":"B","name":"B","x":1,"y":1,"locationType":"NORMAL","latitude":10.780,"longitude":106.710}
                ],"roads":[
                  {"id":"AB","startIntersectionId":"A","endIntersectionId":"B","distance":1,"speedLimit":40,"capacity":10,"currentVehicleCount":0,"congestionLevel":0,"vehicleDensity":0,"roadStatus":"OPEN","geometry":[[10.770,106.700],[10.780,106.710]]}
                ]}
                """;
    }

    private static class CapturingSimulationService implements SimulationService {
        private RoadNetwork network;
        private DemandScenario demandScenario;
        private CapturingSimulationService(RoadNetwork network) { this.network = network; }
        @Override public void replaceNetwork(RoadNetwork network) { this.network = network; }
        @Override public RoadNetwork getNetwork() { return network; }
        @Override public SimulationStatus getStatus() { return SimulationStatus.READY; }
        @Override public void initialize(List<team.traffic.simulation.VehicleConfig> vehicles, List<team.traffic.simulation.EventConfig> events) {}
        @Override
        public void initialize(
                DemandScenario demandScenario,
                List<team.traffic.simulation.VehicleConfig> vehicles,
                List<team.traffic.simulation.EventConfig> events) {
            this.demandScenario = demandScenario;
        }
        @Override public void start() {}
        @Override public void pause() {}
        @Override public void resume() {}
        @Override public void reset() {}
        @Override public void advanceTick() {}
        @Override public void addEvent(TrafficEvent event) {}
        @Override public long getCurrentTick() { return 0; }
        @Override public SimulationSnapshot getSnapshot() { return new SimulationSnapshot(0, getStatus(), List.of(), List.of()); }
        @Override public List<Vehicle> getVehicles() { return List.of(); }
        @Override public List<TrafficEvent> getEvents() { return List.of(); }
    }
}
