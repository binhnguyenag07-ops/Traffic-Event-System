package team.traffic.api;

import io.javalin.Javalin;
import team.traffic.api.controller.HealthController;
import team.traffic.api.controller.NetworkController;
import team.traffic.api.controller.RoutingController;
import team.traffic.api.controller.SimulationController;

public class ApiRouteRegistry {
    private final HealthController healthController;
    private final NetworkController networkController;
    private final RoutingController routingController;
    private final SimulationController simulationController;

    public ApiRouteRegistry(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController,
            SimulationController simulationController) {
        this.healthController = healthController;
        this.networkController = networkController;
        this.routingController = routingController;
        this.simulationController = simulationController;
    }

    public void register(Javalin app) {
        healthController.registerRoutes(app);
        networkController.registerRoutes(app);
        routingController.registerRoutes(app);
        simulationController.registerRoutes(app);
    }
}
