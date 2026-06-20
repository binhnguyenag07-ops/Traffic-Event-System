package team.traffic.api;

import io.javalin.Javalin;
import team.traffic.api.controller.HealthController;
import team.traffic.api.controller.NetworkController;
import team.traffic.api.controller.RoutingController;

public class ApiRouteRegistry {
    private final HealthController healthController;
    private final NetworkController networkController;
    private final RoutingController routingController;

    public ApiRouteRegistry(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController) {
        this.healthController = healthController;
        this.networkController = networkController;
        this.routingController = routingController;
    }

    public void register(Javalin app) {
        healthController.registerRoutes(app);
        networkController.registerRoutes(app);
        routingController.registerRoutes(app);
    }
}
