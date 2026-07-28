package team.traffic;

import java.nio.file.Path;
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
import team.traffic.api.controller.ExperimentController;
import team.traffic.api.controller.PhysicalViewController;
import team.traffic.experiment.ExperimentService;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.observer.TrafficEventBus;
import team.traffic.persistence.JsonMapLoader;
import team.traffic.persistence.JsonScenarioLoader;
import team.traffic.routing.AStarPathFindingStrategy;
import team.traffic.routing.BfsPathFindingStrategy;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
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
    private static final String DEFAULT_SCENARIO_FILE =
            "data/scenarios/district1_typical_day.json";

    public static void main(String[] args) throws Exception {
        /*
         * A normal launch loads a complete scenario, not merely its map. The old
         * boot path claimed District 1 was active while leaving demand
         * uninitialised; because the picker already selected its only real
         * scenario, the UI could not activate it with an onChange event.
         *
         * SCENARIO_FILE has precedence. MAP_FILE remains an explicit topology-
         * only override for map-development workflows.
         */
        String scenarioFile = nonBlankEnvironment("SCENARIO_FILE");
        String mapFile = nonBlankEnvironment("MAP_FILE");
        JsonScenarioLoader.LoadedScenario startupScenario = null;
        RoadNetwork network;
        if (scenarioFile != null || mapFile == null) {
            Path path = Path.of(
                    scenarioFile == null ? DEFAULT_SCENARIO_FILE : scenarioFile);
            startupScenario = new JsonScenarioLoader().load(path);
            network = startupScenario.network();
        } else {
            network = new JsonMapLoader().load(mapFile);
        }
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
        TrafficSimulator simulator = new TrafficSimulator(
                network,
                routingService,
                eventBus,
                team.traffic.simulation.AdaptiveTrafficConfig.defaults());
        SimulationServiceImpl simulationService = new SimulationServiceImpl(simulator);
        if (startupScenario != null) {
            JsonScenarioLoader.LoadedScenario loaded = startupScenario;
            simulationService.configureAdaptiveTraffic(loaded.adaptiveTraffic());
            simulationService.configureDriverDecisionProfiles(loaded.driverProfiles());
            loaded.demand().ifPresentOrElse(
                    demand -> simulationService.initialize(
                            demand, loaded.vehicles(), loaded.events()),
                    () -> simulationService.initialize(
                            loaded.vehicles(), loaded.events()));
            System.out.println("Initialized scenario: " + loaded.id()
                    + " (" + simulationService.getDemandRunStatistics().scheduledTrips()
                    + " demand plans)");
        }
        VehicleServiceImpl vehicleService = new VehicleServiceImpl(simulator, routingService);
        TrafficEventServiceImpl trafficEventService = new TrafficEventServiceImpl(eventBus, networkService, simulator);

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
        ScenarioController scenarioController = new ScenarioController(
                networkService,
                simulationService,
                startupScenario == null ? "imported" : startupScenario.id());
        ExperimentController experimentController = new ExperimentController(new ExperimentService());
        PhysicalViewController physicalViewController =
                new PhysicalViewController(networkService, simulationService);

        // Wire API routes
        ApiRouteRegistry routeRegistry = new ApiRouteRegistry(
                healthController, networkController, routingController, simulationController,
                vehicleController, trafficEventController, statisticsController, scenarioController,
                experimentController, physicalViewController);

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

    private static String nonBlankEnvironment(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : value;
    }
}
