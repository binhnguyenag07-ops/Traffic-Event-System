package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.persistence.JsonScenarioLoader;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;
import team.traffic.service.impl.MapImportService;
import team.traffic.service.impl.OsrmRoadGeometryProvider;

public class ScenarioController {
    private static final Map<String, Scenario> SCENARIOS = scenarios();
    private final RoadNetworkService networkService;
    private final SimulationService simulationService;
    private final JsonScenarioLoader loader;
    private final MapImportService importService;

    public ScenarioController(RoadNetworkService networkService, SimulationService simulationService) {
        this(networkService, simulationService, new MapImportService(new OsrmRoadGeometryProvider()));
    }

    public ScenarioController(RoadNetworkService networkService, SimulationService simulationService,
            MapImportService importService) {
        this.networkService = networkService;
        this.simulationService = simulationService;
        this.loader = new JsonScenarioLoader();
        this.importService = importService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/scenarios", ctx -> ctx.json(SCENARIOS.values()));
        app.post("/api/scenarios/{id}/activate", this::activate);
        app.post("/api/scenarios/import", this::importMap);
    }

    private void importMap(Context ctx) {
        var upload = ctx.uploadedFile("map");
        if (upload == null) {
            ctx.status(400).json(Map.of("error", "Multipart field 'map' is required"));
            return;
        }
        try (var input = upload.content()) {
            MapImportService.ImportResult imported = importService.importJson(upload.filename(), input);
            simulationService.replaceNetwork(imported.network());
            networkService.replaceNetwork(imported.network());
            ctx.json(Map.of(
                    "filename", upload.filename(),
                    "intersectionCount", imported.network().getIntersectionCount(),
                    "roadCount", imported.network().getRoadCount(),
                    "geometrySources", imported.geometrySources(),
                    "bounds", imported.bounds(),
                    "status", simulationService.getStatus().name()));
        } catch (Exception exception) {
            ctx.status(400).json(Map.of("error", exception.getMessage()));
        }
    }

    private void activate(Context ctx) {
        String id = ctx.pathParam("id");
        Scenario scenario = SCENARIOS.get(id);
        if (scenario == null) {
            ctx.status(404).json(Map.of("error", "Scenario not found: " + id));
            return;
        }
        try {
            JsonScenarioLoader.LoadedScenario loaded = loader.load(Path.of(scenario.file()));
            RoadNetwork network = loaded.network();
            networkService.replaceNetwork(network);
            simulationService.replaceNetwork(network);
            simulationService.configureAdaptiveTraffic(loaded.adaptiveTraffic());
            simulationService.configureDriverDecisionProfiles(loaded.driverProfiles());
            loaded.demand().ifPresentOrElse(
                    demand -> simulationService.initialize(
                            demand, loaded.vehicles(), loaded.events()),
                    () -> simulationService.initialize(loaded.vehicles(), loaded.events()));
            ctx.json(Map.of(
                    "scenario", scenario,
                    "intersectionCount", network.getIntersectionCount(),
                    "roadCount", network.getRoadCount(),
                    "buildingCount", network.getBuildingCount(),
                    "demand", simulationService.getDemandRunStatistics(),
                    "status", simulationService.getStatus().name()));
        } catch (Exception exception) {
            ctx.status(400).json(Map.of("error", "Failed to activate scenario: " + exception.getMessage()));
        }
    }

    private static Map<String, Scenario> scenarios() {
        Map<String, Scenario> result = new LinkedHashMap<>();
        result.put("district-1", new Scenario(
                "district-1",
                "District 1 Typical Day",
                "District 1 network with seeded building and boundary demand",
                "data/scenarios/district1_typical_day.json"));
        return Map.copyOf(result);
    }

    public record Scenario(String id, String name, String description, String file) {}
}
