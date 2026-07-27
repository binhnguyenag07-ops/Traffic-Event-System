package team.traffic.domain.routing;

import java.util.Objects;
import java.util.Optional;
import team.traffic.domain.network.JunctionMovement;

/** A fully costed route suffix tied to the route version it was computed from. */
public record RouteCandidate(
        Route route,
        RouteCost expectedCost,
        Optional<JunctionMovement> firstMovement,
        String routeFingerprint,
        String algorithmName,
        long sourceRouteVersion) {

    public RouteCandidate {
        route = Objects.requireNonNull(route, "Candidate route is required.");
        expectedCost = Objects.requireNonNull(expectedCost, "Candidate cost is required.");
        firstMovement = firstMovement == null ? Optional.empty() : firstMovement;
        if (routeFingerprint == null || routeFingerprint.isBlank()) {
            throw new IllegalArgumentException("Route fingerprint is required.");
        }
        if (algorithmName == null || algorithmName.isBlank()) {
            throw new IllegalArgumentException("Algorithm name is required.");
        }
        if (sourceRouteVersion < 0) {
            throw new IllegalArgumentException("Source route version must not be negative.");
        }
    }

    public static String fingerprintOf(Route route) {
        if (route == null) {
            return "NO_ROUTE";
        }
        return route.getSteps().stream()
                .map(RouteStep::getRoadId)
                .reduce((left, right) -> left + ">" + right)
                .orElse("ARRIVED");
    }
}
