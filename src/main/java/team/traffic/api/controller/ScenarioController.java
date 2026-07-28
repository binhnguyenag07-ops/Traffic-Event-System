package team.traffic.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.persistence.JsonScenarioLoader;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;
import team.traffic.service.impl.MapImportService;
import team.traffic.service.impl.OsrmRoadGeometryProvider;

public class ScenarioController {
    private static final Path SCENARIO_ROOT = Path.of("data", "scenarios");
    private static final ObjectMapper DESCRIBER = new ObjectMapper();

    /**
     * Which scenario the loaded network came from.
     *
     * <p>The client used to assume this, and the assumption survived a reload
     * while the server's network did not: activating a lab case replaces the
     * network for good, so a refreshed page believed it was showing District 1
     * while the server held a fifteen-metre test map. It drew the test map on
     * the basemap and District 1 never appeared. Only the server knows, so the
     * server says.</p>
     *
     * <p>Starts as the scenario whose map {@code App} loads at boot.</p>
     */
    private volatile String activeScenarioId;
    private final RoadNetworkService networkService;
    private final SimulationService simulationService;
    private final JsonScenarioLoader loader;
    private final MapImportService importService;

    public ScenarioController(RoadNetworkService networkService, SimulationService simulationService) {
        this(networkService, simulationService, "district-1");
    }

    public ScenarioController(
            RoadNetworkService networkService,
            SimulationService simulationService,
            String initialScenarioId) {
        this(
                networkService,
                simulationService,
                new MapImportService(new OsrmRoadGeometryProvider()),
                initialScenarioId);
    }

    public ScenarioController(RoadNetworkService networkService, SimulationService simulationService,
            MapImportService importService) {
        this(networkService, simulationService, importService, "district-1");
    }

    ScenarioController(
            RoadNetworkService networkService,
            SimulationService simulationService,
            MapImportService importService,
            String initialScenarioId) {
        this.networkService = networkService;
        this.simulationService = simulationService;
        this.loader = new JsonScenarioLoader();
        this.importService = importService;
        this.activeScenarioId = initialScenarioId == null || initialScenarioId.isBlank()
                ? "imported"
                : initialScenarioId;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/scenarios", ctx -> ctx.json(scenarios().values()));
        app.get("/api/scenarios/active", ctx -> ctx.json(Map.of("id", activeScenarioId)));
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
            // An uploaded map belongs to no registered scenario, so nothing
            // downstream should treat it as one.
            activeScenarioId = "imported";
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
        Scenario scenario = scenarios().get(id);
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
            activeScenarioId = scenario.id();
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

    /**
     * Every scenario file under {@code data/scenarios}, read fresh on each call.
     *
     * <p>Rescanning rather than caching is deliberate: the physical-view test
     * cases are generated by a script, and having to restart the server to see a
     * newly generated case would make the lab tedious enough that nobody would
     * use it. A few dozen small files cost nothing to reread.</p>
     *
     * <p>Ordering puts the real scenarios ahead of the {@code test/} cases so the
     * default selection stays a proper run rather than whichever lab case sorts
     * first.</p>
     */
    private static Map<String, Scenario> scenarios() {
        if (!Files.isDirectory(SCENARIO_ROOT)) return Map.of();
        List<Scenario> found = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(SCENARIO_ROOT)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(ScenarioController::describe)
                    .filter(Objects::nonNull)
                    .forEach(found::add);
        } catch (IOException exception) {
            return Map.of();
        }
        found.sort(Comparator
                .comparing((Scenario scenario) -> scenario.lab() != null)
                .thenComparing(Scenario::id));
        Map<String, Scenario> result = new LinkedHashMap<>();
        found.forEach(scenario -> result.putIfAbsent(scenario.id(), scenario));
        // Not Map.copyOf: that returns an unordered map, which would throw away
        // the sort above and leave the picker defaulting to a random lab case.
        return Collections.unmodifiableMap(result);
    }

    /** Reads a scenario's identity without loading its map. Null when unreadable. */
    private static Scenario describe(Path path) {
        try {
            JsonNode root = DESCRIBER.readTree(path.toFile());
            if (root == null || !root.isObject()) return null;
            String stem = path.getFileName().toString().replaceFirst("\\.json$", "");
            JsonNode lab = root.get("lab");
            return new Scenario(
                    text(root, "id", stem),
                    text(root, "name", stem),
                    text(root, "description", ""),
                    path.toString().replace('\\', '/'),
                    lab != null && lab.isObject() ? lab : null);
        } catch (IOException exception) {
            return null;
        }
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.get(field);
        return value != null && value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : fallback;
    }

    /**
     * A scenario as the picker sees it. {@code lab} is present only for the
     * physical-view test cases and carries where to open the view and what the
     * case is supposed to show.
     */
    public record Scenario(String id, String name, String description, String file, JsonNode lab) {}
}
