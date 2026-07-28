package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import team.traffic.domain.demand.CompletionPolicy;
import team.traffic.domain.demand.GravityDemandModel;
import team.traffic.domain.demand.PurposeId;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.demand.TripLeg;
import team.traffic.domain.demand.TripPlan;
import team.traffic.domain.demand.TripPlanReachability;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoutingServiceImpl;
import team.traffic.simulation.TrafficSimulator;
import team.traffic.simulation.VehicleConfig;

class JsonScenarioLoaderTest {
    private static final Path SHIPPED_SCENARIO =
            Path.of("data/scenarios/district1_typical_day.json");

    @Test
    void loadsTheDeterministicCentralDistrict1Scenario() throws Exception {
        JsonScenarioLoader.LoadedScenario loaded =
                new JsonScenarioLoader().load(SHIPPED_SCENARIO);
        var demand = loaded.demand().orElseThrow();

        assertThat(loaded.id()).isEqualTo("district-1");
        assertThat(loaded.name()).isEqualTo("Central District 1 - Synthetic Weekday");
        assertThat(loaded.network().getIntersectionCount()).isGreaterThan(100);
        assertThat(loaded.network().getBuildingCount()).isEqualTo(1162);
        assertThat(demand.seed()).isEqualTo(20260728);
        assertThat(demand.sources()).hasSize(3);
        assertThat(demand.zones()).containsOnlyKeys(
                "north-east-entries",
                "south-west-entries",
                "north-east-exits",
                "south-west-exits",
                "tourism-core");
        assertThat(loaded.vehicles())
                .extracting(VehicleConfig::id)
                .containsExactly(
                        "warm-cau-khanh-hoi-to-ben-thanh",
                        "warm-cach-mang-thang-8-to-bitexco",
                        "warm-nguyen-du-to-saigon-centre",
                        "warm-ton-duc-thang-to-opera-house");
        assertThat(loaded.events()).isEmpty();

        GravityDemandModel first =
                new GravityDemandModel(loaded.network(), demand);
        GravityDemandModel replay =
                new GravityDemandModel(loaded.network(), demand);

        assertThat(first.allPlans()).isEqualTo(replay.allPlans()).hasSize(216);
        assertThat(first.scheduledTripCount()).isEqualTo(216);
        assertThat(first.generationFailedPlanIds()).isEmpty();
        assertThat(first.allPlans())
                .allSatisfy(plan -> assertThat(
                        TripPlanReachability.isReachable(loaded.network(), plan))
                        .isTrue());
        assertThat(first.allPlans().stream()
                        .filter(plan -> plan.completion() == CompletionPolicy.RETURN_TO_ORIGIN))
                .allSatisfy(plan -> assertThat(
                        plan.legs().get(plan.legs().size() - 1).to())
                        .isEqualTo(plan.origin()));
        assertThat(first.allPlans().stream().collect(Collectors.groupingBy(
                        plan -> plan.purpose().value(),
                        Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "COMMUTE", 74L,
                        "INBOUND_WORK", 37L,
                        "MEDICAL", 5L,
                        "SCHOOL_DROPOFF", 10L,
                        "SHOPPING", 20L,
                        "THROUGH", 48L,
                        "VISITOR", 22L));
        assertThat(first.allPlans().stream().collect(Collectors.groupingBy(
                        TripPlan::vehicleType,
                        Collectors.counting())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        VehicleType.MOTORCYCLE, 184L,
                        VehicleType.CAR, 32L));
        assertThat(first.allPlans().get(0).departAtSeconds()).isLessThan(300);
    }

    @Test
    void curatedCordonTourismAndWarmStartRoutesAreDirectedAndRoleCorrect()
            throws Exception {
        JsonScenarioLoader.LoadedScenario loaded =
                new JsonScenarioLoader().load(SHIPPED_SCENARIO);
        RoadNetwork network = loaded.network();
        var zones = loaded.demand().orElseThrow().zones();
        Set<String> northEastEntries =
                zones.get("north-east-entries").intersectionIds();
        Set<String> southWestEntries =
                zones.get("south-west-entries").intersectionIds();
        Set<String> northEastExits =
                zones.get("north-east-exits").intersectionIds();
        Set<String> southWestExits =
                zones.get("south-west-exits").intersectionIds();
        List<String> tourismAccesses = zones.get("tourism-core").buildingIds().stream()
                .map(network::getBuilding)
                .map(building -> building.getAccessPoints().get(0).intersectionId())
                .toList();

        northEastEntries.forEach(entry -> {
            assertThat(network.findOutgoingRoads(entry)).isNotEmpty();
            assertThat(network.findIncomingRoads(entry)).isEmpty();
            southWestExits.forEach(exit -> assertReachable(network, entry, exit));
        });
        southWestEntries.forEach(entry -> {
            assertThat(network.findOutgoingRoads(entry)).isNotEmpty();
            assertThat(network.findIncomingRoads(entry)).isEmpty();
            northEastExits.forEach(exit -> assertReachable(network, entry, exit));
        });
        northEastExits.forEach(exit -> {
            assertThat(network.findIncomingRoads(exit)).isNotEmpty();
            assertThat(network.findOutgoingRoads(exit)).isEmpty();
        });
        southWestExits.forEach(exit -> {
            assertThat(network.findIncomingRoads(exit)).isNotEmpty();
            assertThat(network.findOutgoingRoads(exit)).isEmpty();
        });
        for (String entry : Set.copyOf(concat(northEastEntries, southWestEntries))) {
            tourismAccesses.forEach(destination ->
                    assertReachable(network, entry, destination));
        }
        for (String origin : tourismAccesses) {
            concat(northEastExits, southWestExits).forEach(exit ->
                    assertReachable(network, origin, exit));
        }
        loaded.vehicles().forEach(config ->
                assertReachable(
                        network,
                        config.startIntersectionId(),
                        config.destinationIntersectionId()));
    }

    @Test
    void scenarioInitializationRemovesLegacyPhantomTraffic() throws Exception {
        JsonScenarioLoader.LoadedScenario loaded =
                new JsonScenarioLoader().load(SHIPPED_SCENARIO);
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        TrafficSimulator simulator = new TrafficSimulator(
                loaded.network(),
                new RoutingServiceImpl(registry));

        simulator.initialize(
                loaded.demand().orElseThrow(),
                loaded.vehicles(),
                loaded.events());

        assertThat(loaded.network().getRoads().values()).allSatisfy(road -> {
            assertThat(road.getCurrentVehicleCount()).isZero();
            assertThat(road.getCondition().getVehicleDensity()).isZero();
            assertThat(road.getCondition().getCongestionLevel()).isZero();
            assertThat(road.getCondition().getRoadStatus()).isEqualTo(RoadStatus.OPEN);
        });
        assertThat(simulator.getVehicles())
                .extracting(vehicle -> vehicle.getId())
                .containsExactlyInAnyOrderElementsOf(
                        loaded.vehicles().stream().map(VehicleConfig::id).toList());
    }

    private static List<String> concat(Set<String> first, Set<String> second) {
        return java.util.stream.Stream.concat(first.stream(), second.stream()).toList();
    }

    private static void assertReachable(
            RoadNetwork network,
            String origin,
            String destination) {
        TripPlan probe = new TripPlan(
                "probe-" + origin + "-" + destination,
                new PurposeId("PROBE"),
                VehicleType.CAR,
                ResolvedPlace.intersection(origin),
                List.of(new TripLeg(ResolvedPlace.intersection(destination), 0)),
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                0);
        assertThat(TripPlanReachability.isReachable(network, probe))
                .as("directed path from %s to %s", origin, destination)
                .isTrue();
    }
}
