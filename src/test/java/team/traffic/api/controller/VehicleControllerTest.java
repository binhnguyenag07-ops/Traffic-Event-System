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
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.RoutingService;
import team.traffic.service.VehicleService;

class VehicleControllerTest {
    private Javalin app;

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void createVehicleAcceptsEndIntersectionIdAliasFromFrontend() throws Exception {
        CapturingVehicleService vehicleService = new CapturingVehicleService();
        RoadNetwork network = createNetwork();
        VehicleController controller = new VehicleController(
                vehicleService,
                new StubRoadNetworkService(network));
        app = Javalin.create();
        controller.registerRoutes(app);
        app.start(0);

        HttpResponse<String> response = postJson(
                "/api/vehicles",
                """
                {
                  "type": "car",
                  "startIntersectionId": "A",
                  "endIntersectionId": "B"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(vehicleService.createdDestinationIntersectionId).isEqualTo("B");
        assertThat(response.body()).contains("\"destinationIntersectionId\":\"B\"");
        assertThat(response.body()).contains("\"length\":4.5");
        assertThat(response.body()).contains("\"maxSpeed\":120.0");
        assertThat(response.body()).contains("\"currentSpeed\":50.0");
        assertThat(response.body()).contains("\"priorityLevel\":0");
    }

    @Test
    void createVehicleReturnsBadRequestForServiceValidationErrors() throws Exception {
        VehicleController controller = new VehicleController(
                new CapturingVehicleService(),
                new StubRoadNetworkService(createNetwork()));
        app = Javalin.create();
        controller.registerRoutes(app);
        app.start(0);

        HttpResponse<String> response = postJson(
                "/api/vehicles",
                """
                {
                  "type": "car",
                  "startIntersectionId": "UNKNOWN",
                  "destinationIntersectionId": "B"
                }
                """);

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.body()).contains("Start intersection not found");
    }

    private HttpResponse<String> postJson(String path, String body) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + app.port() + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private RoadNetwork createNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        return network;
    }

    private static class CapturingVehicleService implements VehicleService {
        private final List<Vehicle> vehicles = new ArrayList<>();
        private String createdDestinationIntersectionId;

        @Override
        public List<Vehicle> getAllVehicles() {
            return List.copyOf(vehicles);
        }

        @Override
        public Vehicle getVehicleById(String id) {
            return vehicles.stream()
                    .filter(vehicle -> vehicle.getId().equals(id))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Vehicle createVehicle(
                VehicleType type,
                String startIntersectionId,
                String destinationIntersectionId,
                RoadNetwork network) {
            if (!network.containsIntersection(startIntersectionId)) {
                throw new IllegalArgumentException("Start intersection not found: " + startIntersectionId);
            }
            if (!network.containsIntersection(destinationIntersectionId)) {
                throw new IllegalArgumentException("Destination intersection not found: " + destinationIntersectionId);
            }

            createdDestinationIntersectionId = destinationIntersectionId;
            Vehicle vehicle = new Car("vehicle-1", startIntersectionId, destinationIntersectionId);
            vehicles.add(vehicle);
            return vehicle;
        }

    }

    private static class StubRoadNetworkService implements RoadNetworkService {
        private final RoadNetwork network;

        private StubRoadNetworkService(RoadNetwork network) {
            this.network = network;
        }

        @Override
        public RoadNetwork getNetwork() {
            return network;
        }

        @Override
        public Road recordVehicleEnteringRoad(String roadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Road recordVehicleLeavingRoad(String roadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double updateVehicleDensity(String roadId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public double getAverageVehicleDensity() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Road> findRoadsAtOrAboveDensity(double minimumDensity) {
            throw new UnsupportedOperationException();
        }
    }
}
