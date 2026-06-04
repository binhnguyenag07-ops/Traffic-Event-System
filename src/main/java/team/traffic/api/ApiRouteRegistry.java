package team.traffic.api;

import team.traffic.api.controller.HealthController;
import team.traffic.api.controller.NetworkController;
import team.traffic.api.controller.RoutingController;
import team.traffic.api.controller.SimulationController;
import team.traffic.api.controller.StatisticsController;
import team.traffic.api.controller.TrafficEventController;

public class ApiRouteRegistry {
    private HealthController healthController;
    private NetworkController networkController;
    private RoutingController routingController;
    private SimulationController simulationController;
    private TrafficEventController trafficEventController;
    private StatisticsController statisticsController;

    public void registerControllers(
            HealthController healthController,
            NetworkController networkController,
            RoutingController routingController,
            SimulationController simulationController,
            TrafficEventController trafficEventController,
            StatisticsController statisticsController) {
        this.healthController = healthController;
        this.networkController = networkController;
        this.routingController = routingController;
        this.simulationController = simulationController;
        this.trafficEventController = trafficEventController;
        this.statisticsController = statisticsController;
    }

    public HealthController getHealthController() {
        return healthController;
    }

    public NetworkController getNetworkController() {
        return networkController;
    }

    public RoutingController getRoutingController() {
        return routingController;
    }

    public SimulationController getSimulationController() {
        return simulationController;
    }

    public TrafficEventController getTrafficEventController() {
        return trafficEventController;
    }

    public StatisticsController getStatisticsController() {
        return statisticsController;
    }
}
