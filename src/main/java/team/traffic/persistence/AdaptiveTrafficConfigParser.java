package team.traffic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.network.UTurnNetworkPolicy;
import team.traffic.simulation.AdaptiveTrafficConfig;

/** Parses scenario-wide adaptive traffic settings with explicit units. */
public final class AdaptiveTrafficConfigParser {
    private AdaptiveTrafficConfigParser() {}

    public static AdaptiveTrafficConfig parse(JsonNode node) {
        AdaptiveTrafficConfig defaults = AdaptiveTrafficConfig.defaults();
        if (node == null || node.isNull()) return defaults;
        if (!node.isObject()) {
            throw new IllegalArgumentException("adaptiveTraffic must be an object");
        }
        JsonNode routingNode = object(node, "routing");
        JsonNode congestionNode = object(node, "congestion");
        JsonNode uTurnsNode = object(node, "uTurns");
        JsonNode deadlockNode = object(node, "deadlock");

        return new AdaptiveTrafficConfig(
                bool(node, "enabled", defaults.enabled()),
                number(
                        node,
                        "strategicCadenceSeconds",
                        defaults.strategicCadenceSeconds()),
                new AdaptiveTrafficConfig.Routing(
                        integer(
                                routingNode,
                                "maximumLookaheadRoads",
                                defaults.routing().maximumLookaheadRoads()),
                        number(
                                routingNode,
                                "knowledgeCacheSeconds",
                                defaults.routing().knowledgeCacheSeconds()),
                        backoff(
                                routingNode == null
                                        ? null
                                        : routingNode.get("retryBackoffSeconds"),
                                defaults.routing().retryBackoffSeconds())),
                new AdaptiveTrafficConfig.Congestion(number(
                        congestionNode,
                        "travelTimeEmaHalfLifeSeconds",
                        defaults.congestion().travelTimeEmaHalfLifeSeconds())),
                new AdaptiveTrafficConfig.UTurns(
                        enumValue(
                                uTurnsNode,
                                "networkPolicy",
                                UTurnNetworkPolicy.class,
                                defaults.uTurns().networkPolicy()),
                        number(
                                uTurnsNode,
                                "defaultPenaltySeconds",
                                defaults.uTurns().defaultPenaltySeconds())),
                new AdaptiveTrafficConfig.Deadlock(
                        number(
                                deadlockNode,
                                "scanIntervalSeconds",
                                defaults.deadlock().scanIntervalSeconds()),
                        number(
                                deadlockNode,
                                "confirmSeconds",
                                defaults.deadlock().confirmSeconds()),
                        number(
                                deadlockNode,
                                "minimumProgressMetres",
                                defaults.deadlock().minimumProgressMetres()),
                        number(
                                deadlockNode,
                                "reservationSeconds",
                                defaults.deadlock().reservationSeconds()),
                        assistedRecovery(deadlockNode)));
    }

    private static JsonNode object(JsonNode parent, String field) {
        if (parent == null) return null;
        JsonNode value = parent.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
        }
        return value;
    }

    private static double number(JsonNode node, String field, double fallback) {
        if (node == null) return fallback;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return value.asDouble();
    }

    private static int integer(JsonNode node, String field, int fallback) {
        if (node == null) return fallback;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isIntegralNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return value.asInt();
    }

    private static boolean bool(JsonNode node, String field, boolean fallback) {
        if (node == null) return fallback;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.asBoolean();
    }

    private static boolean assistedRecovery(JsonNode deadlockNode) {
        if (deadlockNode == null) return false;
        JsonNode value = deadlockNode.get("assistedRecovery");
        if (value == null || value.isNull()) return false;
        if (value.isBoolean()) return value.asBoolean();
        if (value.isTextual()) {
            return switch (value.asText().toUpperCase()) {
                case "DISABLED" -> false;
                case "ENABLED" -> true;
                default -> throw new IllegalArgumentException(
                        "assistedRecovery must be ENABLED or DISABLED");
            };
        }
        throw new IllegalArgumentException(
                "assistedRecovery must be a boolean or ENABLED/DISABLED");
    }

    private static List<Double> backoff(
            JsonNode node,
            List<Double> fallback) {
        if (node == null || node.isNull()) return fallback;
        if (!node.isArray() || node.isEmpty()) {
            throw new IllegalArgumentException(
                    "retryBackoffSeconds must be a non-empty array");
        }
        List<Double> result = new ArrayList<>();
        node.forEach(value -> {
            if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
                throw new IllegalArgumentException(
                        "retryBackoffSeconds values must be finite numbers");
            }
            result.add(value.asDouble());
        });
        return List.copyOf(result);
    }

    private static <E extends Enum<E>> E enumValue(
            JsonNode node,
            String field,
            Class<E> type,
            E fallback) {
        if (node == null) return fallback;
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        try {
            return Enum.valueOf(type, value.asText().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Unknown " + field + ": " + value.asText(), exception);
        }
    }
}
