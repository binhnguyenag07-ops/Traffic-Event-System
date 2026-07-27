package team.traffic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.event.EventOrigin;
import team.traffic.domain.event.InformationPolicy;
import team.traffic.simulation.EventConfig;
import team.traffic.simulation.VehicleConfig;

/** Shared parser for the legacy scripted parts of a scenario payload. */
public final class ScenarioConfigParser {
    private ScenarioConfigParser() {}

    public static List<VehicleConfig> parseVehicles(JsonNode vehiclesNode) {
        List<VehicleConfig> configs = new ArrayList<>();
        if (vehiclesNode == null || vehiclesNode.isNull()) return List.of();
        if (!vehiclesNode.isArray()) {
            throw new IllegalArgumentException("vehicles must be an array");
        }
        for (JsonNode node : vehiclesNode) {
            String id = textOrDefault(node, "id", "vehicle-" + configs.size());
            String type = textOrDefault(node, "type", "CAR");
            String start = firstNonNull(node, "currentIntersectionId", "startIntersectionId");
            String destination = firstNonNull(
                    node, "destinationIntersectionId", "endIntersectionId");
            if (start == null || destination == null) {
                throw new IllegalArgumentException(
                        "Scripted vehicle " + id + " requires start and destination intersections");
            }
            configs.add(new VehicleConfig(id, type, start, destination));
        }
        return List.copyOf(configs);
    }

    public static List<EventConfig> parseEvents(JsonNode eventsNode) {
        List<EventConfig> configs = new ArrayList<>();
        if (eventsNode == null || eventsNode.isNull()) return List.of();
        if (!eventsNode.isArray()) {
            throw new IllegalArgumentException("events must be an array");
        }
        for (JsonNode node : eventsNode) {
            String id = textOrDefault(node, "id", "event-" + configs.size());
            String type = textOrDefault(node, "type", "CONGESTION");
            String roadId = firstNonNull(node, "affectedRoadId", "roadId");
            if (roadId == null) {
                throw new IllegalArgumentException("Event " + id + " requires an affected road");
            }
            double startTimeSeconds = numberWithLegacyAlias(
                    node, "startTimeSeconds", "startTick", 0.0);
            double durationSeconds = numberWithLegacyAlias(
                    node, "durationSeconds", "durationTicks", 10.0);
            Double value = nullableNumber(node, "value");
            String causingVehicleId = firstNonNull(
                    node, "causingVehicleId", "affectedVehicleId");
            Double positionAlongRoad = nullableNumber(node, "positionAlongRoad");
            List<Integer> blockedLaneIndexes = new ArrayList<>();
            JsonNode blockedLanes = node.get("blockedLaneIndexes");
            if (blockedLanes != null && !blockedLanes.isNull()) {
                if (!blockedLanes.isArray()) {
                    throw new IllegalArgumentException(
                            "blockedLaneIndexes must be an array for event " + id);
                }
                blockedLanes.forEach(valueNode -> blockedLaneIndexes.add(valueNode.asInt()));
            }
            configs.add(new EventConfig(
                    id,
                    type,
                    roadId,
                    startTimeSeconds,
                    durationSeconds,
                    value,
                    causingVehicleId,
                    positionAlongRoad,
                    blockedLaneIndexes,
                    enumValue(node, "origin", EventOrigin.class, EventOrigin.SCENARIO),
                    informationPolicy(node.get("informationPolicy"))));
        }
        return List.copyOf(configs);
    }

    private static InformationPolicy informationPolicy(JsonNode node) {
        if (node == null || node.isNull()) {
            return InformationPolicy.broadcastOnActivation();
        }
        String kindText;
        Double publicationSeconds = null;
        if (node.isTextual()) {
            kindText = node.asText();
        } else if (node.isObject()) {
            kindText = textOrDefault(
                    node, "kind", InformationPolicy.Kind.BROADCAST_ON_ACTIVATION.name());
            publicationSeconds = nullableNumber(node, "publicationSeconds");
        } else {
            throw new IllegalArgumentException(
                    "informationPolicy must be a string or object");
        }
        InformationPolicy.Kind kind;
        try {
            kind = InformationPolicy.Kind.valueOf(kindText.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown informationPolicy kind: " + kindText, exception);
        }
        return switch (kind) {
            case LOCAL_ONLY -> InformationPolicy.localOnly();
            case BROADCAST_ON_ACTIVATION -> InformationPolicy.broadcastOnActivation();
            case ANNOUNCED_AT -> {
                if (publicationSeconds == null) {
                    throw new IllegalArgumentException(
                            "ANNOUNCED_AT requires publicationSeconds");
                }
                yield InformationPolicy.announcedAt(publicationSeconds);
            }
        };
    }

    private static <E extends Enum<E>> E enumValue(
            JsonNode node,
            String field,
            Class<E> type,
            E defaultValue) {
        String value = textOrDefault(node, field, defaultValue.name());
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown " + field + ": " + value, exception);
        }
    }

    private static double numberWithLegacyAlias(
            JsonNode node,
            String field,
            String legacyField,
            double defaultValue) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) value = node.get(legacyField);
        if (value == null || value.isNull()) return defaultValue;
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return value.asDouble();
    }

    private static Double nullableNumber(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return value.asDouble();
    }

    private static String textOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value != null && !value.isNull() && !value.asText().isBlank()
                ? value.asText()
                : defaultValue;
    }

    private static String firstNonNull(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText().isBlank()) {
                return value.asText();
            }
        }
        return null;
    }
}
