package team.traffic;

import team.traffic.api.ApiRouteRegistry;
import team.traffic.api.TrafficApiServer;
import team.traffic.api.controller.HealthController;
import team.traffic.api.controller.NetworkController;
import team.traffic.api.controller.RoutingController;
import team.traffic.api.controller.SimulationController;
import team.traffic.api.controller.StatisticsController;
import team.traffic.api.controller.TrafficEventController;
import team.traffic.api.controller.VehicleController;
import team.traffic.api.controller.ScenarioController;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.observer.TrafficEventBus;
import team.traffic.persistence.JsonMapLoader;
import team.traffic.routing.AStarPathFindingStrategy;
import team.traffic.routing.BfsPathFindingStrategy;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.routing.DynamicReroutingListener;
import team.traffic.service.impl.RoadNetworkServiceImpl;
import team.traffic.service.impl.NetworkEditingServiceImpl;
import team.traffic.service.impl.OsrmRoadGeometryProvider;
import team.traffic.service.impl.RoutingServiceImpl;
import team.traffic.service.impl.SimulationServiceImpl;
import team.traffic.service.impl.StatisticsServiceImpl;
import team.traffic.simulation.TrafficSimulator;
import team.traffic.service.impl.TrafficEventServiceImpl;
import team.traffic.service.impl.VehicleServiceImpl;

public class App {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "7070"));
    private static final String MAP_FILE = System.getenv().getOrDefault("MAP_FILE", "data/small_city.json");

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

        // Create core buses
        TrafficEventBus eventBus = new TrafficEventBus();

        // Create services
        RoadNetworkServiceImpl networkService = new RoadNetworkServiceImpl(network);
        RoutingServiceImpl routingService = new RoutingServiceImpl(registry);
        TrafficSimulator simulator = new TrafficSimulator(network, routingService, eventBus);
        SimulationServiceImpl simulationService = new SimulationServiceImpl(simulator);
        VehicleServiceImpl vehicleService = new VehicleServiceImpl(simulator);
        TrafficEventServiceImpl trafficEventService = new TrafficEventServiceImpl(eventBus, networkService, simulator);
        DynamicReroutingListener reroutingListener = new DynamicReroutingListener(
                simulator::getNetwork,
                routingService,
                () -> simulator.getVehicles());
        eventBus.subscribe(reroutingListener);

        // Create controllers
        HealthController healthController = new HealthController();
        NetworkEditingServiceImpl networkEditingService = new NetworkEditingServiceImpl(
                simulator, simulationService, routingService, new OsrmRoadGeometryProvider());
        NetworkController networkController = new NetworkController(networkService, networkEditingService);
        RoutingController routingController = new RoutingController(routingService, networkService);
        SimulationController simulationController = new SimulationController(simulationService);

        VehicleController vehicleController = new VehicleController(vehicleService, networkService);
        TrafficEventController trafficEventController = new TrafficEventController(trafficEventService, simulationService);
        StatisticsServiceImpl statisticsService = new StatisticsServiceImpl(
                simulationService, networkService, registry, routingService);
        StatisticsController statisticsController = new StatisticsController(statisticsService);
        ScenarioController scenarioController = new ScenarioController(networkService, simulationService);

        // Wire API routes
        ApiRouteRegistry routeRegistry = new ApiRouteRegistry(
                healthController, networkController, routingController, simulationController,
                vehicleController, trafficEventController, statisticsController, scenarioController);

        // Start Javalin server
        TrafficApiServer server = new TrafficApiServer(routeRegistry, PORT);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            simulationService.close();
            server.stop();
        }, "traffic-shutdown"));
        server.start();

        System.out.println("Traffic API Server running on http://localhost:" + PORT);
        System.out.println("  POST http://localhost:" + PORT + "/api/routes");
        System.out.println("  GET  http://localhost:" + PORT + "/api/health");
        System.out.println("  GET  http://localhost:" + PORT + "/api/network/intersections");
        System.out.println("  POST http://localhost:" + PORT + "/api/simulation/initialize");
        System.out.println("  POST http://localhost:" + PORT + "/api/simulation/start");
        System.out.println("  POST http://localhost:" + PORT + "/api/simulation/pause");
        System.out.println("  POST http://localhost:" + PORT + "/api/simulation/reset");
        System.out.println("  POST http://localhost:" + PORT + "/api/simulation/tick");
        System.out.println("  GET  http://localhost:" + PORT + "/api/simulation/status");
        System.out.println("  GET  http://localhost:" + PORT + "/api/simulation/snapshot");
        System.out.println("  GET  http://localhost:" + PORT + "/api/vehicles");
        System.out.println("  GET  http://localhost:" + PORT + "/api/events");
        System.out.println("  GET  http://localhost:" + PORT + "/api/events/active");
        System.out.println("  GET  http://localhost:" + PORT + "/api/events/road/{roadId}");
        System.out.println("  PUT  http://localhost:" + PORT + "/api/events/{id}/status");
        System.out.println("  DELETE http://localhost:" + PORT + "/api/events/{id}");
        System.out.println("  GET  http://localhost:" + PORT + "/api/statistics");
        System.out.println("  GET  http://localhost:" + PORT + "/api/statistics/traffic");
        System.out.println("  GET  http://localhost:" + PORT + "/api/statistics/algorithm/{name}");
    }
}
