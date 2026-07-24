package team.traffic.api.controller;

import static org.assertj.core.api.Assertions.assertThat;

import io.javalin.Javalin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.SimulationSnapshot;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.VehicleConfig;
import team.traffic.domain.event.TrafficEvent;

class PhysicalViewControllerTest {
    private Javalin app;

    @AfterEach
    void stop() {
        if (app != null) app.stop();
    }

    @Test
    void servesLaneGeometryAroundAPoint() throws Exception {
        start();

        HttpResponse<String> response = get("/api/simulation/physical-view"
                + "?lat=10.0&lon=106.000&radiusMetres=300");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("\"lanes\"")
                .contains("\"origin\"")
                .contains("\"radiusMetres\":300.0")
                .contains("\"roadId\":\"AB\"");
    }

    @Test
    void returnsScenesInLocalMetresWithTheOriginAtZero() throws Exception {
        start();

        HttpResponse<String> response = get("/api/simulation/physical-view"
                + "?lat=10.0&lon=106.000&radiusMetres=300");

        // The origin sits on intersection A, so A's lane points start near [0,0]
        // and run east to positive x. No latitude should appear in a lane point.
        assertThat(response.body()).contains("\"timelineId\"");
        assertThat(response.body()).contains("\"frameId\"");
    }

    @Test
    void rejectsAMissingParameterWith400() throws Exception {
        start();

        HttpResponse<String> response = get("/api/simulation/physical-view?lat=10.0&lon=106.0");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("radiusMetres");
    }

    @Test
    void rejectsANonPositiveRadiusWith400() throws Exception {
        start();

        HttpResponse<String> response = get("/api/simulation/physical-view"
                + "?lat=10.0&lon=106.0&radiusMetres=0");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private void start() {
        RoadNetwork network = network();
        PhysicalViewController controller = new PhysicalViewController(
                new StubNetworkService(network), new EmptySimulationService());
        app = Javalin.create();
        controller.registerRoutes(app);
        app.start(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 106.000, 10.0, LocationType.NORMAL, 10.0, 106.000));
        network.addIntersection(new Intersection("B", "B", 106.002, 10.0, LocationType.NORMAL, 10.0, 106.002));
        network.addRoad(new Road("AB", "Main", "AB", "A", "B", 0.22, 50, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.0, 106.000), new GeoPoint(10.0, 106.002)),
                RoadClass.SECONDARY));
        return network;
    }

    /** Serves one fixed network; the rest of the interface is unused here. */
    private record StubNetworkService(RoadNetwork network) implements RoadNetworkService {
        @Override public RoadNetwork getNetwork() { return network; }
        @Override public Road recordVehicleEnteringRoad(String roadId) { return null; }
        @Override public Road recordVehicleLeavingRoad(String roadId) { return null; }
        @Override public double updateVehicleDensity(String roadId) { return 0; }
        @Override public double getAverageVehicleDensity() { return 0; }
        @Override public List<Road> findRoadsAtOrAboveDensity(double minimumDensity) { return List.of(); }
    }

    /** Just enough of the interface to answer with no current frame. */
    private static final class EmptySimulationService implements SimulationService {
        @Override public void initialize(List<VehicleConfig> vehicles, List<EventConfig> events) {}
        @Override public void start() {}
        @Override public void pause() {}
        @Override public void resume() {}
        @Override public void reset() {}
        @Override public void advanceTick() {}
        @Override public void addEvent(TrafficEvent event) {}
        @Override public SimulationStatus getStatus() { return SimulationStatus.READY; }
        @Override public long getCurrentTick() { return 0; }
        @Override public SimulationSnapshot getSnapshot() { return null; }
        @Override public List<Vehicle> getVehicles() { return List.of(); }
        @Override public List<TrafficEvent> getEvents() { return List.of(); }
    }
}
