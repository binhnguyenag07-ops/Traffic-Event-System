package team.traffic;

import team.traffic.api.ApiRouteRegistry;
import team.traffic.api.TrafficApiServer;
import team.traffic.api.controller.HealthController;
import team.traffic.api.controller.NetworkController;
import team.traffic.api.controller.RoutingController;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.persistence.JsonMapLoader;
import team.traffic.routing.AStarPathFindingStrategy;
import team.traffic.routing.BfsPathFindingStrategy;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoadNetworkServiceImpl;
import team.traffic.service.impl.RoutingServiceImpl;

public class App {
    private static final int PORT = 7070;
    private static final String MAP_FILE = "data/small_city.json";

    public static void main(String[] args) throws Exception {
        // Load road network from JSON
        JsonMapLoader loader = new JsonMapLoader();
        RoadNetwork network = loader.load(MAP_FILE);
        System.out.println("Loaded road network: "
                + network.getIntersectionCount() + " intersections, "
                + network.getRoadCount() + " roads");

        // Register routing strategies (order determines priority for shared preferences)
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new BfsPathFindingStrategy());           // MINIMUM_HOPS
        registry.register(new DijkstraPathFindingStrategy());      // SHORTEST_DISTANCE, SHORTEST_TIME, CONGESTION_AWARE
        registry.register(new AStarPathFindingStrategy(            // SHORTEST_TIME, CONGESTION_AWARE (secondary)
                (a, b) -> Math.sqrt(
                        Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2))));

        // Create services
        RoadNetworkServiceImpl networkService = new RoadNetworkServiceImpl(network);
        RoutingServiceImpl routingService = new RoutingServiceImpl(registry);

        // Create controllers
        HealthController healthController = new HealthController();
        NetworkController networkController = new NetworkController(networkService);
        RoutingController routingController = new RoutingController(routingService, networkService);

        // Wire API routes
        ApiRouteRegistry routeRegistry = new ApiRouteRegistry(
                healthController, networkController, routingController);

        // Start Javalin server
        TrafficApiServer server = new TrafficApiServer(routeRegistry, PORT);
        server.start();

        System.out.println("Traffic API Server running on http://localhost:" + PORT);
        System.out.println("  POST http://localhost:" + PORT + "/api/routes");
        System.out.println("  GET  http://localhost:" + PORT + "/api/health");
        System.out.println("  GET  http://localhost:" + PORT + "/api/network/intersections");
    }
}
