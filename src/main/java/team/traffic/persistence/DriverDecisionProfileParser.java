package team.traffic.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.EnumMap;
import java.util.Map;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.navigation.DriverDecisionProfile;
import team.traffic.domain.vehicle.navigation.InformationAccess;
import team.traffic.domain.vehicle.navigation.UTurnPolicy;

/** Data-defined strategic driver catalog keyed by vehicle type. */
public final class DriverDecisionProfileParser {
    private DriverDecisionProfileParser() {}

    public static Map<VehicleType, DriverDecisionProfile> parse(JsonNode node) {
        if (node == null || node.isNull()) return Map.of();
        if (!node.isObject()) {
            throw new IllegalArgumentException("driverProfiles must be an object");
        }
        Map<VehicleType, DriverDecisionProfile> result =
                new EnumMap<>(VehicleType.class);
        node.fields().forEachRemaining(entry -> {
            VehicleType type;
            try {
                type = VehicleType.valueOf(entry.getKey().toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Unknown driver vehicle type: " + entry.getKey(), exception);
            }
            JsonNode wrapper = entry.getValue();
            JsonNode decision = wrapper != null && wrapper.has("decision")
                    ? wrapper.get("decision")
                    : wrapper;
            if (decision == null || !decision.isObject()) {
                throw new IllegalArgumentException(
                        "Driver profile for " + type + " must contain a decision object");
            }
            DriverDecisionProfile fallback = DriverDecisionProfile.forType(type);
            result.put(type, new DriverDecisionProfile(
                    enumValue(
                            decision,
                            "informationAccess",
                            InformationAccess.class,
                            fallback.informationAccess()),
                    number(
                            decision,
                            "navigationHorizonSeconds",
                            fallback.navigationHorizonSeconds()),
                    number(
                            decision,
                            "informationLatencySeconds",
                            fallback.informationLatencySeconds()),
                    number(
                            decision,
                            "noticeReceptionProbability",
                            fallback.noticeReceptionProbability()),
                    number(
                            decision,
                            "reroutePatienceSeconds",
                            fallback.reroutePatienceSeconds()),
                    number(
                            decision,
                            "minimumExpectedSavingSeconds",
                            fallback.minimumExpectedSavingSeconds()),
                    number(
                            decision,
                            "rerouteCooldownSeconds",
                            fallback.rerouteCooldownSeconds()),
                    number(
                            decision,
                            "stallEscapeSeconds",
                            fallback.stallEscapeSeconds()),
                    enumValue(
                            decision,
                            "uTurnPolicy",
                            UTurnPolicy.class,
                            fallback.uTurnPolicy())));
        });
        return Map.copyOf(result);
    }

    private static double number(JsonNode node, String field, double fallback) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return fallback;
        if (!value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw new IllegalArgumentException(field + " must be a finite number");
        }
        return value.asDouble();
    }

    private static <E extends Enum<E>> E enumValue(
            JsonNode node,
            String field,
            Class<E> type,
            E fallback) {
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
