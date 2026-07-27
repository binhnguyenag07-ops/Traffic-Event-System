package team.traffic.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.AdaptiveRouteRequest;
import team.traffic.domain.routing.RerouteReason;
import team.traffic.domain.routing.RouteAnchor;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.domain.vehicle.navigation.DriverKnowledge;
import team.traffic.domain.vehicle.navigation.UTurnPolicy;

class AdaptiveRouterTest {
    @Test
    void aRoadAtMomentaryCapacityRemainsInTheRoutingGraph() {
        RoadNetwork network = new RoadNetwork();
        addJunctions(network);
        network.addRoad(road("AB", "physical", "A", "B", 1, 1));
        AdaptiveRouteRequest request = request(
                new RouteAnchor.AtJunction("A", Optional.empty()),
                "B",
                UTurnPolicy.NEVER);

        var candidate = new AdaptiveRouter().findRoute(
                network,
                RoutingSnapshot.freeFlow(network, List.of(), 0.0),
                request);

        assertThat(candidate.route().getSteps())
                .extracting(step -> step.getRoadId())
                .containsExactly("AB");
        assertThat(RoutingSnapshot.freeFlow(network, List.of(), 0.0)
                .road("AB").temporarilyFull()).isTrue();
    }

    @Test
    void reverseEdgeAloneDoesNotAuthorizeUTurnOnPrimaryRoad() {
        RoadNetwork network = reversePair();
        AdaptiveRouteRequest request = request(
                new RouteAnchor.OnRoad("AB", 0.5, "B"),
                "A",
                UTurnPolicy.WHEN_MATERIALLY_BETTER);

        assertThatThrownBy(() -> new AdaptiveRouter().findRoute(
                network,
                RoutingSnapshot.freeFlow(network, List.of(), 0.0),
                request))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void explicitLegalUTurnIsUsedByEdgeStateSearch() {
        RoadNetwork network = reversePair();
        network.getMovementGraph().register(new JunctionMovement(
                "B",
                Optional.of("AB"),
                "BA",
                TurnDirection.U_TURN,
                MovementPermission.ALLOWED,
                Set.of(VehicleType.CAR),
                20.0,
                MovementProvenance.EXPLICIT));

        var candidate = new AdaptiveRouter().findRoute(
                network,
                RoutingSnapshot.freeFlow(network, List.of(), 0.0),
                request(
                        new RouteAnchor.OnRoad("AB", 0.5, "B"),
                        "A",
                        UTurnPolicy.WHEN_MATERIALLY_BETTER));

        assertThat(candidate.firstMovement()).isPresent()
                .get().extracting(JunctionMovement::direction)
                .isEqualTo(TurnDirection.U_TURN);
        assertThat(candidate.route().getSteps())
                .extracting(step -> step.getRoadId())
                .containsExactly("BA");
    }

    @Test
    void routePreferenceStillControlsAdaptiveSearchObjective() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 2, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 1, 1, LocationType.NORMAL));
        network.addRoad(road("AB", "AB", "A", "B", 10.0, 100.0));
        network.addRoad(road("AC", "AC", "A", "C", 1.0, 10.0));
        network.addRoad(road("CB", "CB", "C", "B", 1.0, 10.0));
        AdaptiveRouter router = new AdaptiveRouter();
        RoutingSnapshot snapshot = RoutingSnapshot.freeFlow(network, List.of(), 0.0);

        var fastest = router.findRoute(
                network,
                snapshot,
                request(
                        new RouteAnchor.AtJunction("A", Optional.empty()),
                        "B",
                        UTurnPolicy.NEVER,
                        RoutePreference.SHORTEST_TIME));
        var shortest = router.findRoute(
                network,
                snapshot,
                request(
                        new RouteAnchor.AtJunction("A", Optional.empty()),
                        "B",
                        UTurnPolicy.NEVER,
                        RoutePreference.SHORTEST_DISTANCE));

        assertThat(fastest.route().getSteps())
                .extracting(step -> step.getRoadId())
                .containsExactly("AB");
        assertThat(shortest.route().getSteps())
                .extracting(step -> step.getRoadId())
                .containsExactly("AC", "CB");
    }

    @Test
    void virtualBatchDemandCanDistributeEquivalentDrivers() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 1, LocationType.NORMAL));
        network.addIntersection(new Intersection("C", "C", 1, -1, LocationType.NORMAL));
        network.addIntersection(new Intersection("D", "D", 2, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "AB", "A", "B", 1.0, 50.0));
        network.addRoad(road("BD", "BD", "B", "D", 1.0, 50.0));
        network.addRoad(road("AC", "AC", "A", "C", 1.0, 50.0));
        network.addRoad(road("CD", "CD", "C", "D", 1.0, 50.0));
        AdaptiveRouter router = new AdaptiveRouter();
        AdaptiveRouteRequest request = request(
                new RouteAnchor.AtJunction("A", Optional.empty()),
                "D",
                UTurnPolicy.NEVER,
                RoutePreference.CONGESTION_AWARE);
        RoutingSnapshot firstSnapshot =
                RoutingSnapshot.freeFlow(network, List.of(), 0.0);

        var first = router.findRoute(network, firstSnapshot, request);
        RoutingSnapshot secondSnapshot =
                firstSnapshot.withVirtualDemand(first.route());
        var second = router.findRoute(network, secondSnapshot, request);

        assertThat(first.route().getSteps().get(0).getRoadId())
                .isNotEqualTo(second.route().getSteps().get(0).getRoadId());
        assertThat(firstSnapshot.virtualDemandSignature()).isEqualTo("NONE");
        assertThat(secondSnapshot.virtualDemandSignature()).isNotEqualTo("NONE");
    }

    private static AdaptiveRouteRequest request(
            RouteAnchor anchor,
            String destination,
            UTurnPolicy uTurnPolicy) {
        return request(
                anchor,
                destination,
                uTurnPolicy,
                RoutePreference.CONGESTION_AWARE);
    }

    private static AdaptiveRouteRequest request(
            RouteAnchor anchor,
            String destination,
            UTurnPolicy uTurnPolicy,
            RoutePreference preference) {
        return new AdaptiveRouteRequest(
                "car",
                VehicleType.CAR,
                anchor,
                destination,
                preference,
                new DriverKnowledge(),
                Set.of(),
                uTurnPolicy,
                RerouteReason.STALL_TIMEOUT,
                0);
    }

    private static RoadNetwork reversePair() {
        RoadNetwork network = new RoadNetwork();
        addJunctions(network);
        network.addRoad(road("AB", "physical", "A", "B", 10, 0));
        network.addRoad(road("BA", "physical", "B", "A", 10, 0));
        return network;
    }

    private static void addJunctions(RoadNetwork network) {
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
    }

    private static Road road(
            String id,
            String physicalId,
            String from,
            String to,
            int capacity,
            int currentVehicles) {
        return new Road(
                id,
                id,
                physicalId,
                from,
                to,
                1.0,
                50,
                capacity,
                currentVehicles,
                1,
                3.5,
                new RoadCondition(
                        currentVehicles == capacity ? 1.0 : 0.0,
                        capacity == 0 ? 0.0 : (double) currentVehicles / capacity,
                        RoadStatus.OPEN),
                List.of(),
                RoadClass.PRIMARY);
    }

    private static Road road(
            String id,
            String physicalId,
            String from,
            String to,
            double distanceKm,
            double speedLimitKph) {
        return new Road(
                id,
                id,
                physicalId,
                from,
                to,
                distanceKm,
                speedLimitKph,
                100,
                0,
                1,
                3.5,
                new RoadCondition(0.0, 0.0, RoadStatus.OPEN),
                List.of(),
                RoadClass.PRIMARY);
    }
}
