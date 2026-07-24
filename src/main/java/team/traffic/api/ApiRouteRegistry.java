package team.traffic.api;

import io.javalin.Javalin;
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

public class ApiRouteRegistry {
    private final HealthController healthController;
    private final NetworkController networkController;
    private final RoutingController routingController;
    private final SimulationController simulationController;
    private final VehicleController vehicleController;
    private final TrafficEventController trafficEventController;
    private final StatisticsController statisticsController;
    private final ScenarioController scenarioController;
    private final ExperimentController experimentController;
    private final PhysicalViewController physicalViewController;

    public ApiRouteRegistry(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController,
            SimulationController simulationController,
            VehicleController vehicleController,
            TrafficEventController trafficEventController,
            StatisticsController statisticsController) {
        this(healthController, networkController, routingController, simulationController,
                vehicleController, trafficEventController, statisticsController, null, null);
    }

    public ApiRouteRegistry(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController,
            SimulationController simulationController,
            VehicleController vehicleController,
            TrafficEventController trafficEventController,
            StatisticsController statisticsController,
            ScenarioController scenarioController) {
        this(healthController, networkController, routingController, simulationController,
                vehicleController, trafficEventController, statisticsController, scenarioController, null);
    }

    public ApiRouteRegistry(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController,
            SimulationController simulationController,
            VehicleController vehicleController,
            TrafficEventController trafficEventController,
            StatisticsController statisticsController,
            ScenarioController scenarioController,
            ExperimentController experimentController) {
        this(healthController, networkController, routingController, simulationController,
                vehicleController, trafficEventController, statisticsController,
                scenarioController, experimentController, null);
    }

    public ApiRouteRegistry(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController,
            SimulationController simulationController,
            VehicleController vehicleController,
            TrafficEventController trafficEventController,
            StatisticsController statisticsController,
            ScenarioController scenarioController,
            ExperimentController experimentController,
            PhysicalViewController physicalViewController) {
        this.healthController = healthController;
        this.networkController = networkController;
        this.routingController = routingController;
        this.simulationController = simulationController;
        this.vehicleController = vehicleController;
        this.trafficEventController = trafficEventController;
        this.statisticsController = statisticsController;
        this.scenarioController = scenarioController;
        this.experimentController = experimentController;
        this.physicalViewController = physicalViewController;
    }

    public void register(Javalin app) {
        healthController.registerRoutes(app);
        networkController.registerRoutes(app);
        routingController.registerRoutes(app);
        simulationController.registerRoutes(app);
        vehicleController.registerRoutes(app);
        trafficEventController.registerRoutes(app);
        statisticsController.registerRoutes(app);
        if (scenarioController != null) {
            scenarioController.registerRoutes(app);
        }
        if (experimentController != null) {
            experimentController.registerRoutes(app);
        }
        if (physicalViewController != null) {
            physicalViewController.registerRoutes(app);
        }
    }
}
