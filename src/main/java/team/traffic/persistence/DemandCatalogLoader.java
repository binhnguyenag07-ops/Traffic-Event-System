package team.traffic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import team.traffic.domain.demand.BuildingDemandParameters;
import team.traffic.domain.demand.DemandCatalog;
import team.traffic.domain.demand.DemandCurve;
import team.traffic.domain.demand.DemandParameters;
import team.traffic.domain.network.BuildingType;

public final class DemandCatalogLoader {
    private final ObjectMapper objectMapper;

    public DemandCatalogLoader() {
        this(new ObjectMapper());
    }

    DemandCatalogLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DemandCatalog load(Path path) throws IOException {
        if (path == null) throw new IllegalArgumentException("Demand catalog path is required");
        JsonNode root = objectMapper.readTree(path.toFile());
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Demand catalog root must be an object");
        }
        String status = requiredText(root, "status");
        String note = text(root, "note", "");
        double beta = requiredPositiveOrZero(root, "distanceDecayBeta");

        JsonNode buildingTypes = requiredObject(root, "buildingTypes");
        Map<BuildingType, BuildingDemandParameters> parameters = new EnumMap<>(BuildingType.class);
        for (BuildingType type : BuildingType.values()) {
            JsonNode node = buildingTypes.get(type.name());
            if (node == null || !node.isObject()) {
                throw new IllegalArgumentException("Missing building demand parameters for " + type);
            }
            parameters.put(type, new BuildingDemandParameters(
                    requiredPositiveOrZero(node, "occupantsPerSquareMetre"),
                    requiredPositiveOrZero(node, "fallbackOccupants"),
                    requiredPositiveOrZero(node, "attractionWeight")));
        }

        JsonNode curvesNode = requiredObject(root, "curves");
        Map<String, DemandCurve> curves = new LinkedHashMap<>();
        curvesNode.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isArray()) {
                throw new IllegalArgumentException("Demand curve must be an array: " + entry.getKey());
            }
            List<Double> values = new ArrayList<>();
            entry.getValue().forEach(value -> values.add(value.asDouble(Double.NaN)));
            curves.put(entry.getKey(), new DemandCurve(entry.getKey(), values));
        });

        return new DemandCatalog(
                status,
                note,
                new DemandParameters(parameters, beta),
                curves);
    }

    private static JsonNode requiredObject(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        String value = text(node, field, null);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        return value;
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? fallback : value.asText();
    }

    private static double requiredPositiveOrZero(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(field + " must be numeric");
        double number = value.asDouble();
        if (!Double.isFinite(number) || number < 0) {
            throw new IllegalArgumentException(field + " must be finite and non-negative");
        }
        return number;
    }
}
