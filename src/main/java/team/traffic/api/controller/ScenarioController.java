package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.persistence.JsonMapLoader;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;

public class ScenarioController {
    private static final Map<String, Scenario> SCENARIOS = scenarios();
    private final RoadNetworkService networkService;
    private final SimulationService simulationService;
    private final JsonMapLoader loader;

    public ScenarioController(RoadNetworkService networkService, SimulationService simulationService) {
        this.networkService = networkService;
        this.simulationService = simulationService;
        this.loader = new JsonMapLoader();
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/scenarios", ctx -> ctx.json(SCENARIOS.values()));
        app.post("/api/scenarios/{id}/activate", this::activate);
    }

    private void activate(Context ctx) {
        String id = ctx.pathParam("id");
        Scenario scenario = SCENARIOS.get(id);
        if (scenario == null) {
            ctx.status(404).json(Map.of("error", "Scenario not found: " + id));
            return;
        }
        try {
            RoadNetwork network = loader.load(Path.of(scenario.file()).toString());
            networkService.replaceNetwork(network);
            simulationService.replaceNetwork(network);
            ctx.json(Map.of(
                    "scenario", scenario,
                    "intersectionCount", network.getIntersectionCount(),
                    "roadCount", network.getRoadCount()));
        } catch (Exception exception) {
            ctx.status(400).json(Map.of("error", "Failed to activate scenario: " + exception.getMessage()));
        }
    }

    private static Map<String, Scenario> scenarios() {
        Map<String, Scenario> result = new LinkedHashMap<>();
        result.put("small-city", new Scenario("small-city", "Small City", "Balanced traffic and alternative routes", "data/small_city.json"));
        result.put("congestion", new Scenario("congestion", "Rush Hour", "Heavy congestion with viable detours", "data/congestion_demo.json"));
        result.put("accident", new Scenario("accident", "Accident Response", "Blocked central road and emergency rerouting", "data/accident_demo.json"));
        result.put("density", new Scenario("density", "Density Stress", "Roads from low load to full capacity", "data/density_demo.json"));
        return Map.copyOf(result);
    }

    public record Scenario(String id, String name, String description, String file) {}
}
