package team.traffic.domain.demand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class GravityDemandModelTest {
    @Test
    void permitsTheSameFlowIdInDifferentSourcesWithoutSharingCachedDestinations() {
        RoadNetwork network = new RoadNetwork();
        for (String id : List.of("A", "B", "C")) {
            network.addIntersection(new Intersection(id, id, 0, 0, LocationType.NORMAL));
        }
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("AC", "A", "C"));
        DemandCurve hourSix = new DemandCurve(
                "HOUR_SIX",
                java.util.stream.IntStream.range(0, 24)
                        .mapToObj(hour -> hour == 6 ? 1.0 : 0.0)
                        .toList());
        FlowSpec firstFlow = new FlowSpec(
                "shared-flow-id",
                new PurposeId("FIRST_SOURCE"),
                1.0,
                List.of(new VehicleMixEntry(VehicleType.CAR, 1.0)),
                hourSix,
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                List.of(new LegSpec(
                        PlaceSpec.explicitIntersection("B"),
                        DwellSpec.fixed(0))));
        FlowSpec secondFlow = new FlowSpec(
                "shared-flow-id",
                new PurposeId("SECOND_SOURCE"),
                1.0,
                List.of(new VehicleMixEntry(VehicleType.MOTORCYCLE, 1.0)),
                hourSix,
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                List.of(new LegSpec(
                        PlaceSpec.explicitIntersection("C"),
                        DwellSpec.fixed(0))));
        DemandScenario scenario = new DemandScenario(
                6 * 3600,
                3600,
                93,
                1,
                List.of(
                        new DemandSource(
                                "first-source",
                                PlaceSpec.explicitIntersection("A"),
                                ProductionSpec.absolute(1),
                                List.of(firstFlow)),
                        new DemandSource(
                                "second-source",
                                PlaceSpec.explicitIntersection("A"),
                                ProductionSpec.absolute(1),
                                List.of(secondFlow))),
                Map.of(),
                parameters());

        GravityDemandModel model = new GravityDemandModel(network, scenario);
        GravityDemandModel replay = new GravityDemandModel(network, scenario);

        assertThat(model.allPlans()).isEqualTo(replay.allPlans()).hasSize(2);
        TripPlan firstPlan = model.allPlans().stream()
                .filter(plan -> plan.purpose().value().equals("FIRST_SOURCE"))
                .findFirst()
                .orElseThrow();
        TripPlan secondPlan = model.allPlans().stream()
                .filter(plan -> plan.purpose().value().equals("SECOND_SOURCE"))
                .findFirst()
                .orElseThrow();
        assertThat(firstPlan.id()).startsWith("first-source-shared-flow-id-");
        assertThat(firstPlan.legs()).extracting(TripLeg::to)
                .containsExactly(ResolvedPlace.intersection("B"));
        assertThat(secondPlan.id()).startsWith("second-source-shared-flow-id-");
        assertThat(secondPlan.legs()).extracting(TripLeg::to)
                .containsExactly(ResolvedPlace.intersection("C"));
    }

    @Test
    void generatesDeterministicClosedPlansForAnOpenPurposeIdentifier() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BA", "B", "A"));
        DemandCurve hourSix = new DemandCurve(
                "HOUR_SIX",
                java.util.stream.IntStream.range(0, 24)
                        .mapToObj(hour -> hour == 6 ? 1.0 : 0.0)
                        .toList());
        FlowSpec delivery = new FlowSpec(
                "delivery",
                new PurposeId("DELIVERY_WITH_PICKUP"),
                1.0,
                List.of(new VehicleMixEntry(VehicleType.MOTORCYCLE, 1.0)),
                hourSix,
                CompletionPolicy.RETURN_TO_ORIGIN,
                List.of(new LegSpec(
                        PlaceSpec.explicitIntersection("B"),
                        DwellSpec.fixed(90))));
        DemandSource source = new DemandSource(
                "depot",
                PlaceSpec.explicitIntersection("A"),
                ProductionSpec.absolute(10),
                List.of(delivery));
        DemandScenario scenario = new DemandScenario(
                6 * 3600,
                3600,
                77,
                1,
                List.of(source),
                Map.of(),
                parameters());

        GravityDemandModel first = new GravityDemandModel(network, scenario);
        GravityDemandModel replay = new GravityDemandModel(network, scenario);

        assertThat(first.allPlans()).isEqualTo(replay.allPlans()).hasSize(10);
        assertThat(first.generationFailedPlanIds()).isEmpty();
        TripPlan earliest = first.allPlans().get(0);
        TripPlan second = first.allPlans().get(1);
        assertThat(first.tripsBeginningIn(-1e-9, earliest.departAtSeconds()))
                .containsExactly(earliest);
        assertThat(first.tripsBeginningIn(
                        earliest.departAtSeconds(),
                        second.departAtSeconds()))
                .containsExactly(second);
        assertThat(first.tripsBeginningIn(
                        earliest.departAtSeconds(),
                        earliest.departAtSeconds()))
                .isEmpty();
        assertThat(first.allPlans())
                .allSatisfy(plan -> {
                    assertThat(plan.purpose().value()).isEqualTo("DELIVERY_WITH_PICKUP");
                    assertThat(plan.legs()).hasSize(2);
                    assertThat(plan.legs().get(0).to())
                            .isEqualTo(ResolvedPlace.intersection("B"));
                    assertThat(plan.legs().get(1).to()).isEqualTo(plan.origin());
                    assertThat(plan.completion()).isEqualTo(CompletionPolicy.RETURN_TO_ORIGIN);
                });
    }

    @Test
    void rejectsAnAbsoluteFlowWithNoReachableCompletionChain() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "A", "B"));
        DemandCurve hourSix = new DemandCurve(
                "HOUR_SIX",
                java.util.stream.IntStream.range(0, 24)
                        .mapToObj(hour -> hour == 6 ? 1.0 : 0.0)
                        .toList());
        FlowSpec flow = new FlowSpec(
                "closed",
                new PurposeId("CLOSED"),
                1,
                List.of(new VehicleMixEntry(VehicleType.CAR, 1)),
                hourSix,
                CompletionPolicy.RETURN_TO_ORIGIN,
                List.of(new LegSpec(
                        PlaceSpec.explicitIntersection("B"),
                        DwellSpec.fixed(0))));
        DemandScenario scenario = new DemandScenario(
                6 * 3600,
                3600,
                1,
                1,
                List.of(new DemandSource(
                        "source",
                        PlaceSpec.explicitIntersection("A"),
                        ProductionSpec.absolute(1),
                        List.of(flow))),
                Map.of(),
                parameters());

        assertThatThrownBy(() -> new GravityDemandModel(network, scenario))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source")
                .hasMessageContaining("closed")
                .hasMessageContaining("no structurally reachable");
    }

    @Test
    void samplesOnlyCandidatesThatCanReachEveryLaterLegAndReturnToOrigin() {
        RoadNetwork network = new RoadNetwork();
        for (String id : List.of("A", "B", "C", "D")) {
            network.addIntersection(new Intersection(id, id, 0, 0, LocationType.NORMAL));
        }
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("AC", "A", "C"));
        network.addRoad(road("BD", "B", "D"));
        network.addRoad(road("DA", "D", "A"));
        DemandCurve hourSix = new DemandCurve(
                "HOUR_SIX",
                java.util.stream.IntStream.range(0, 24)
                        .mapToObj(hour -> hour == 6 ? 1.0 : 0.0)
                        .toList());
        FlowSpec flow = new FlowSpec(
                "multi-leg",
                new PurposeId("MULTI_LEG"),
                1,
                List.of(new VehicleMixEntry(VehicleType.MOTORCYCLE, 1)),
                hourSix,
                CompletionPolicy.RETURN_TO_ORIGIN,
                List.of(
                        new LegSpec(
                                PlaceSpec.zones(Set.of("first-stops")),
                                DwellSpec.fixed(0)),
                        new LegSpec(
                                PlaceSpec.explicitIntersection("D"),
                                DwellSpec.fixed(0))));
        DemandScenario scenario = new DemandScenario(
                6 * 3600,
                3600,
                81,
                1,
                List.of(new DemandSource(
                        "source",
                        PlaceSpec.explicitIntersection("A"),
                        ProductionSpec.absolute(20),
                        List.of(flow))),
                Map.of("first-stops", new ZoneDefinition(
                        "first-stops",
                        Set.of(),
                        Set.of("B", "C"))),
                parameters());

        GravityDemandModel model = new GravityDemandModel(network, scenario);

        assertThat(model.allPlans()).hasSize(20);
        assertThat(model.generationFailedPlanIds()).isEmpty();
        assertThat(model.allPlans()).allSatisfy(plan -> {
            assertThat(plan.legs()).hasSize(3);
            assertThat(plan.legs().get(0).to())
                    .isEqualTo(ResolvedPlace.intersection("B"));
            assertThat(plan.legs().get(1).to())
                    .isEqualTo(ResolvedPlace.intersection("D"));
            assertThat(plan.legs().get(2).to()).isEqualTo(plan.origin());
            assertThat(TripPlanReachability.isReachable(network, plan)).isTrue();
        });
    }

    private static DemandParameters parameters() {
        EnumMap<BuildingType, BuildingDemandParameters> values =
                new EnumMap<>(BuildingType.class);
        Arrays.stream(BuildingType.values()).forEach(type ->
                values.put(type, new BuildingDemandParameters(0.01, 1, 1)));
        return new DemandParameters(values, 0.25);
    }

    private static Road road(String id, String from, String to) {
        return new Road(
                id,
                from,
                to,
                1,
                40,
                20,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
