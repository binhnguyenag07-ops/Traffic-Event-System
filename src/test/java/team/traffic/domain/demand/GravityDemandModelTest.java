package team.traffic.domain.demand;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
    void recordsGeneratedPlansWithAnUnreachableReturnLeg() {
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

        GravityDemandModel model = new GravityDemandModel(network, scenario);

        assertThat(model.scheduledTripCount()).isEqualTo(1);
        assertThat(model.allPlans()).isEmpty();
        assertThat(model.generationFailedPlanIds()).hasSize(1);
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
