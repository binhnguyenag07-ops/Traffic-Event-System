package team.traffic.domain.routing;

import java.util.Optional;

/** The physical point from which future route steps may be replaced. */
public sealed interface RouteAnchor permits RouteAnchor.AtJunction, RouteAnchor.OnRoad {

    record AtJunction(
            String junctionId,
            Optional<String> incomingRoadId) implements RouteAnchor {
        public AtJunction {
            if (junctionId == null || junctionId.isBlank()) {
                throw new IllegalArgumentException("Junction id is required.");
            }
            incomingRoadId = incomingRoadId == null ? Optional.empty() : incomingRoadId;
            incomingRoadId = incomingRoadId.filter(value -> !value.isBlank());
        }
    }

    record OnRoad(
            String roadId,
            double progress,
            String exitJunctionId) implements RouteAnchor {
        public OnRoad {
            if (roadId == null || roadId.isBlank() || exitJunctionId == null || exitJunctionId.isBlank()) {
                throw new IllegalArgumentException("Road and exit junction ids are required.");
            }
            if (!Double.isFinite(progress) || progress < 0 || progress > 1) {
                throw new IllegalArgumentException("Road progress must be between 0 and 1.");
            }
        }
    }
}
