package team.traffic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import team.traffic.domain.demand.BoundarySide;
import team.traffic.domain.demand.CompletionPolicy;
import team.traffic.domain.demand.DemandCatalog;
import team.traffic.domain.demand.DemandScenario;
import team.traffic.domain.demand.DemandSource;
import team.traffic.domain.demand.DwellSpec;
import team.traffic.domain.demand.FlowSpec;
import team.traffic.domain.demand.LegSpec;
import team.traffic.domain.demand.PlaceKind;
import team.traffic.domain.demand.PlaceResolver;
import team.traffic.domain.demand.PlaceSpec;
import team.traffic.domain.demand.ProductionSpec;
import team.traffic.domain.demand.PurposeId;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.demand.VehicleMixEntry;
import team.traffic.domain.demand.ZoneDefinition;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.VehicleType;

/** Parses and validates the demand portion shared by file and HTTP scenarios. */
public final class DemandScenarioParser {
    public static final String DEFAULT_CATALOG = "data/demand/default_assumptions.json";

    private final DemandCatalogLoader catalogLoader;

    public DemandScenarioParser() {
        this.catalogLoader = new DemandCatalogLoader();
    }

    public Optional<DemandScenario> parseOptional(
            JsonNode root,
            RoadNetwork network,
            Path baseDirectory) throws IOException {
        JsonNode sourcesNode = root == null ? null : root.get("demandSources");
        if (sourcesNode == null || sourcesNode.isNull()) return Optional.empty();
        if (!sourcesNode.isArray()) throw new IllegalArgumentException("demandSources must be an array");
        if (sourcesNode.isEmpty()) {
            throw new IllegalArgumentException("demandSources must not be empty");
        }

        Path base = baseDirectory == null ? Path.of(".") : baseDirectory;
        String catalogName = text(root, "demandParameters", DEFAULT_CATALOG);
        DemandCatalog catalog = catalogLoader.load(resolvePath(base, catalogName));
        Map<String, ZoneDefinition> zones = parseZones(root.get("zones"));
        List<DemandSource> sources = new ArrayList<>();
        for (JsonNode source : sourcesNode) {
            sources.add(parseSource(source, catalog));
        }

        DemandScenario scenario = new DemandScenario(
                parseStartTime(root),
                parseDurationSeconds(root),
                parseSeed(root),
                number(root, "scale", 1.0),
                sources,
                zones,
                catalog.parameters());
        validatePlaces(scenario, network);
        return Optional.of(scenario);
    }

    private DemandSource parseSource(JsonNode node, DemandCatalog catalog) {
        String id = requiredText(node, "id");
        PlaceSpec from = parsePlace(required(node, "from"));
        JsonNode productionNode = required(node, "production");
        ProductionSpec.Kind productionKind = enumValue(
                ProductionSpec.Kind.class, requiredText(productionNode, "kind"), "production kind");
        ProductionSpec production = switch (productionKind) {
            case POPULATION_DERIVED -> {
                rejectPresent(productionNode, "vehiclesPerDay", productionKind);
                yield ProductionSpec.population(
                        requiredNumber(productionNode, "tripsPerPersonPerDay"));
            }
            case ABSOLUTE -> {
                rejectPresent(productionNode, "tripsPerPersonPerDay", productionKind);
                yield ProductionSpec.absolute(requiredNumber(productionNode, "vehiclesPerDay"));
            }
        };

        JsonNode flowsNode = required(node, "flows");
        if (!flowsNode.isArray()) throw new IllegalArgumentException("flows must be an array in source " + id);
        List<FlowSpec> flows = new ArrayList<>();
        for (JsonNode flow : flowsNode) flows.add(parseFlow(flow, catalog));
        return new DemandSource(id, from, production, flows);
    }

    private FlowSpec parseFlow(JsonNode node, DemandCatalog catalog) {
        String id = requiredText(node, "id");
        PurposeId purpose = new PurposeId(requiredText(node, "purpose"));
        double share = requiredNumber(node, "share");
        List<VehicleMixEntry> vehicleMix = parseVehicleMix(node);
        String curveId = requiredText(node, "curve");
        CompletionPolicy completion = enumValue(
                CompletionPolicy.class, requiredText(node, "completion"), "completion policy");
        JsonNode legsNode = required(node, "legs");
        if (!legsNode.isArray()) throw new IllegalArgumentException("legs must be an array in flow " + id);
        List<LegSpec> legs = new ArrayList<>();
        for (JsonNode leg : legsNode) {
            PlaceSpec to = parsePlace(required(leg, "to"));
            legs.add(new LegSpec(to, parseDwell(leg.get("dwell"))));
        }
        return new FlowSpec(id, purpose, share, vehicleMix, catalog.requireCurve(curveId), completion, legs);
    }

    private List<VehicleMixEntry> parseVehicleMix(JsonNode flow) {
        JsonNode mixNode = flow.get("vehicleMix");
        if (mixNode == null && flow.hasNonNull("vehicleType")) {
            return List.of(new VehicleMixEntry(
                    enumValue(VehicleType.class, flow.get("vehicleType").asText(), "vehicle type"), 1.0));
        }
        if (mixNode == null || !mixNode.isArray()) {
            throw new IllegalArgumentException("vehicleMix must be an array");
        }
        List<VehicleMixEntry> mix = new ArrayList<>();
        for (JsonNode item : mixNode) {
            mix.add(new VehicleMixEntry(
                    enumValue(VehicleType.class, requiredText(item, "type"), "vehicle type"),
                    requiredNumber(item, "share")));
        }
        return mix;
    }

    private DwellSpec parseDwell(JsonNode node) {
        if (node == null || node.isNull()) return DwellSpec.fixed(0);
        DwellSpec.Kind kind = enumValue(DwellSpec.Kind.class, requiredText(node, "kind"), "dwell kind");
        return switch (kind) {
            case FIXED -> DwellSpec.fixed(requiredNumber(node, "seconds"));
            case RANGE -> DwellSpec.range(
                    requiredNumber(node, "minSeconds"),
                    requiredNumber(node, "maxSeconds"));
        };
    }

    private PlaceSpec parsePlace(JsonNode node) {
        PlaceKind kind = enumValue(PlaceKind.class, requiredText(node, "kind"), "place kind");
        return switch (kind) {
            case BUILDING -> PlaceSpec.buildings(parseBuildingTypes(required(node, "types")));
            case BOUNDARY -> PlaceSpec.boundary(enumValue(
                    BoundarySide.class, requiredText(node, "side"), "boundary side"));
            case ZONE -> PlaceSpec.zones(parseStrings(required(node, "zoneIds"), "zoneIds"));
            case EXPLICIT -> parseExplicitPlace(node);
            case ORIGIN -> PlaceSpec.origin();
            case INTERSECTION ->
                    throw new IllegalArgumentException("INTERSECTION is not valid in scenario PlaceSpec");
        };
    }

    private Map<String, ZoneDefinition> parseZones(JsonNode zonesNode) {
        if (zonesNode == null || zonesNode.isNull()) return Map.of();
        if (!zonesNode.isArray()) throw new IllegalArgumentException("zones must be an array");
        Map<String, ZoneDefinition> zones = new LinkedHashMap<>();
        for (JsonNode node : zonesNode) {
            String id = requiredText(node, "id");
            ZoneDefinition zone = new ZoneDefinition(
                    id,
                    parseOptionalStrings(node.get("buildingIds")),
                    parseOptionalStrings(node.get("intersectionIds")));
            if (zones.putIfAbsent(id, zone) != null) {
                throw new IllegalArgumentException("Duplicate zone id: " + id);
            }
        }
        return Map.copyOf(zones);
    }

    private void validatePlaces(DemandScenario scenario, RoadNetwork network) {
        PlaceResolver resolver = new PlaceResolver(network, scenario.zones());
        for (DemandSource source : scenario.sources()) {
            List<ResolvedPlace> origins = resolver.resolve(source.from(), null);
            if (source.production().kind() == ProductionSpec.Kind.POPULATION_DERIVED
                    && origins.stream().noneMatch(place -> place.kind() == PlaceKind.BUILDING)) {
                throw new IllegalArgumentException(
                        "Population-derived source resolves no building: " + source.id());
            }
            ResolvedPlace sampleOrigin = origins.get(0);
            for (FlowSpec flow : source.flows()) {
                for (LegSpec leg : flow.legs()) resolver.resolve(leg.to(), sampleOrigin);
            }
        }
    }

    private double parseStartTime(JsonNode root) {
        if (root.has("startTimeOfDaySeconds")) {
            return root.get("startTimeOfDaySeconds").asDouble(Double.NaN);
        }
        String value = text(root, "startTimeOfDay", "06:00");
        String[] parts = value.split(":");
        if (parts.length != 2) throw new IllegalArgumentException("startTimeOfDay must be HH:mm");
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours < 0 || hours > 23 || minutes < 0 || minutes > 59) {
                throw new IllegalArgumentException("startTimeOfDay must be HH:mm");
            }
            return hours * 3_600.0 + minutes * 60.0;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("startTimeOfDay must be HH:mm", exception);
        }
    }

    private double parseDurationSeconds(JsonNode root) {
        if (root.hasNonNull("durationSeconds") && root.hasNonNull("durationHours")) {
            throw new IllegalArgumentException(
                    "Declare only one of durationSeconds or durationHours");
        }
        if (root.has("durationSeconds")) return requiredNumber(root, "durationSeconds");
        return requiredNumber(root, "durationHours") * 3_600.0;
    }

    private long parseSeed(JsonNode root) {
        JsonNode value = root.get("seed");
        if (value == null || value.isNull()) return 0;
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException("seed must be an integer");
        }
        return value.asLong();
    }

    private PlaceSpec parseExplicitPlace(JsonNode node) {
        boolean hasBuilding = node.hasNonNull("buildingId")
                && !node.get("buildingId").asText().isBlank();
        boolean hasIntersection = node.hasNonNull("intersectionId")
                && !node.get("intersectionId").asText().isBlank();
        if (hasBuilding == hasIntersection) {
            throw new IllegalArgumentException(
                    "EXPLICIT place requires exactly one buildingId or intersectionId");
        }
        return hasBuilding
                ? PlaceSpec.explicitBuilding(node.get("buildingId").asText())
                : PlaceSpec.explicitIntersection(node.get("intersectionId").asText());
    }

    private void rejectPresent(
            JsonNode production,
            String field,
            ProductionSpec.Kind kind) {
        if (production.hasNonNull(field)) {
            throw new IllegalArgumentException(
                    "Production kind " + kind + " must not declare " + field);
        }
    }

    private static Path resolvePath(Path base, String configured) {
        Path raw = Path.of(configured);
        if (raw.isAbsolute()) return raw.normalize();
        Path relative = base.resolve(raw).normalize();
        return Files.exists(relative) ? relative : raw.normalize();
    }

    private static Set<BuildingType> parseBuildingTypes(JsonNode node) {
        Set<BuildingType> types = EnumSet.noneOf(BuildingType.class);
        for (String value : parseStrings(node, "types")) {
            types.add(enumValue(BuildingType.class, value, "building type"));
        }
        return types;
    }

    private static Set<String> parseOptionalStrings(JsonNode node) {
        return node == null || node.isNull() ? Set.of() : parseStrings(node, "list");
    }

    private static Set<String> parseStrings(JsonNode node, String field) {
        if (node == null || !node.isArray()) throw new IllegalArgumentException(field + " must be an array");
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.asText().isBlank()) {
                throw new IllegalArgumentException(field + " values must be non-blank strings");
            }
            values.add(value.asText());
        }
        return Set.copyOf(values);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asText();
    }

    private static double requiredNumber(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isNumber()) throw new IllegalArgumentException(field + " must be numeric");
        double number = value.asDouble();
        if (!Double.isFinite(number)) throw new IllegalArgumentException(field + " must be finite");
        return number;
    }

    private static double number(JsonNode node, String field, double fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asDouble(Double.NaN);
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, String label) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Unknown " + label + ": " + value, exception);
        }
    }
}
