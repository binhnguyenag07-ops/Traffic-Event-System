package team.traffic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import team.traffic.domain.demand.DemandScenario;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.VehicleConfig;
import team.traffic.simulation.AdaptiveTrafficConfig;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.navigation.DriverDecisionProfile;
import java.util.Map;

/** Loads one complete run: map, generated demand, events and scripted vehicles. */
public final class JsonScenarioLoader {
    private final ObjectMapper objectMapper;
    private final JsonMapLoader mapLoader;
    private final DemandScenarioParser demandParser;

    public JsonScenarioLoader() {
        this.objectMapper = new ObjectMapper();
        this.mapLoader = new JsonMapLoader();
        this.demandParser = new DemandScenarioParser();
    }

    public LoadedScenario load(Path scenarioPath) throws IOException {
        if (scenarioPath == null) throw new IllegalArgumentException("Scenario path is required");
        Path normalized = scenarioPath.toAbsolutePath().normalize();
        JsonNode root = objectMapper.readTree(normalized.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Scenario root must be an object");
        }

        Path baseDirectory = normalized.getParent();
        Path mapPath = resolvePath(baseDirectory, requiredText(root, "map"));
        RoadNetwork network = mapLoader.load(mapPath.toString());
        Optional<DemandScenario> demand = demandParser.parseOptional(root, network, baseDirectory);
        List<VehicleConfig> vehicles = ScenarioConfigParser.parseVehicles(root.get("vehicles"));
        List<EventConfig> events = ScenarioConfigParser.parseEvents(root.get("events"));
        AdaptiveTrafficConfig adaptiveTraffic =
                AdaptiveTrafficConfigParser.parse(root.get("adaptiveTraffic"));
        Map<VehicleType, DriverDecisionProfile> driverProfiles =
                DriverDecisionProfileParser.parse(root.get("driverProfiles"));

        return new LoadedScenario(
                text(root, "id", fileStem(normalized)),
                text(root, "name", fileStem(normalized)),
                text(root, "description", ""),
                normalized,
                mapPath,
                network,
                demand,
                vehicles,
                events,
                adaptiveTraffic,
                driverProfiles);
    }

    static Path resolvePath(Path baseDirectory, String configured) {
        Path raw = Path.of(configured);
        if (raw.isAbsolute()) return requireExisting(raw.normalize());
        Path relative = baseDirectory.resolve(raw).normalize();
        if (Files.exists(relative)) return relative;
        return requireExisting(raw.toAbsolutePath().normalize());
    }

    private static Path requireExisting(Path path) {
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Referenced file does not exist: " + path);
        }
        return path;
    }

    private static String requiredText(JsonNode root, String field) {
        String value = text(root, field, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private static String text(JsonNode root, String field, String fallback) {
        JsonNode value = root.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static String fileStem(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public record LoadedScenario(
            String id,
            String name,
            String description,
            Path scenarioPath,
            Path mapPath,
            RoadNetwork network,
            Optional<DemandScenario> demand,
            List<VehicleConfig> vehicles,
            List<EventConfig> events,
            AdaptiveTrafficConfig adaptiveTraffic,
            Map<VehicleType, DriverDecisionProfile> driverProfiles) {
        public LoadedScenario {
            demand = demand == null ? Optional.empty() : demand;
            vehicles = List.copyOf(vehicles);
            events = List.copyOf(events);
            if (adaptiveTraffic == null) {
                adaptiveTraffic = AdaptiveTrafficConfig.defaults();
            }
            driverProfiles = driverProfiles == null ? Map.of() : Map.copyOf(driverProfiles);
        }
    }
}
