package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.demand.CompletionPolicy;
import team.traffic.domain.demand.PurposeId;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.demand.ScriptedDemandModel;
import team.traffic.domain.demand.TripLeg;
import team.traffic.domain.demand.TripPlan;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoutingServiceImpl;

class DemandBackendIntegrationTest {
    @Test
    void spawnsOneVehicleThatDwellsReturnsAndIsArchivedOnlyAfterTheWholePlan() {
        RoadNetwork network = bidirectionalNetwork();
        ResolvedPlace origin = ResolvedPlace.intersection("A");
        TripPlan plan = new TripPlan(
                "plan-1",
                new PurposeId("ARBITRARY_LOOP"),
                VehicleType.CAR,
                origin,
                List.of(
                        new TripLeg(ResolvedPlace.intersection("B"), 30),
                        new TripLeg(origin, 0)),
                CompletionPolicy.RETURN_TO_ORIGIN,
                0);
        TrafficSimulator simulator = new TrafficSimulator(network, routing());
        simulator.setSimulationSecondsPerTick(10);
        simulator.initialize(
                new ScriptedDemandModel(List.of(plan)),
                6 * 3600,
                List.of(),
                List.of());

        assertThat(simulator.getVehicles()).hasSize(1);
        assertThat(simulator.getDemandRunStatistics().scheduledTrips()).isEqualTo(1);

        simulator.getVehicles().get(0).moveTo("B");
        simulator.advanceTick();
        assertThat(simulator.getVehicles().get(0).getStatus())
                .isEqualTo(VehicleStatus.DWELLING);
        assertThat(simulator.getVehicles().get(0).getCurrentTripLegIndex()).isZero();

        simulator.advanceTick();
        assertThat(simulator.getVehicles().get(0).getStatus())
                .isEqualTo(VehicleStatus.DWELLING);

        simulator.advanceTick();
        assertThat(simulator.getVehicles().get(0).getCurrentTripLegIndex()).isEqualTo(1);
        assertThat(simulator.getVehicles().get(0).getDestinationIntersectionId()).isEqualTo("A");

        simulator.getVehicles().get(0).moveTo("A");
        simulator.advanceTick();
        assertThat(simulator.getVehicles().get(0).isJourneyComplete()).isTrue();
        assertThat(simulator.getCompletedTrips()).isEmpty();

        for (int tick = 0; tick < 3 && !simulator.getVehicles().isEmpty(); tick++) {
            simulator.advanceTick();
        }
        assertThat(simulator.getVehicles()).isEmpty();
        assertThat(simulator.getCompletedTrips()).hasSize(1);
        assertThat(simulator.getCompletedTrips().get(0).tripPlanId()).isEqualTo("plan-1");
        assertThat(simulator.getCompletedTrips().get(0).tripPurpose()).isEqualTo("ARBITRARY_LOOP");
    }

    @Test
    void honorsDwellAtTheLastStopBeforeCompletingAnOpenPlan() {
        RoadNetwork network = bidirectionalNetwork();
        TripPlan plan = new TripPlan(
                "open-plan",
                new PurposeId("ONE_WAY_VISIT"),
                VehicleType.MOTORCYCLE,
                ResolvedPlace.intersection("A"),
                List.of(new TripLeg(ResolvedPlace.intersection("B"), 20)),
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                0);
        TrafficSimulator simulator = new TrafficSimulator(network, routing());
        simulator.setSimulationSecondsPerTick(10);
        simulator.initialize(
                new ScriptedDemandModel(List.of(plan)),
                0,
                List.of(),
                List.of());

        simulator.getVehicles().get(0).moveTo("B");
        simulator.advanceTick();
        assertThat(simulator.getVehicles().get(0).getStatus())
                .isEqualTo(VehicleStatus.DWELLING);
        assertThat(simulator.getVehicles().get(0).isJourneyComplete()).isFalse();

        simulator.advanceTick();
        assertThat(simulator.getVehicles().get(0).getStatus())
                .isEqualTo(VehicleStatus.ARRIVED);
        assertThat(simulator.getVehicles().get(0).isJourneyComplete()).isTrue();
    }

    @Test
    void reportsAPlanWhoseLaterLegHasNoDirectedPathInsteadOfSpawningIt() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "A", "B"));
        ResolvedPlace origin = ResolvedPlace.intersection("A");
        TripPlan plan = new TripPlan(
                "unreachable-return",
                new PurposeId("CLOSED_LOOP"),
                VehicleType.CAR,
                origin,
                List.of(
                        new TripLeg(ResolvedPlace.intersection("B"), 0),
                        new TripLeg(origin, 0)),
                CompletionPolicy.RETURN_TO_ORIGIN,
                0);
        TrafficSimulator simulator = new TrafficSimulator(network, routing());

        simulator.initialize(
                new ScriptedDemandModel(List.of(plan)),
                0,
                List.of(),
                List.of());

        assertThat(simulator.getVehicles()).isEmpty();
        assertThat(simulator.getDemandRunStatistics().scheduledTrips()).isEqualTo(1);
        assertThat(simulator.getDemandRunStatistics().failedTrips()).isEqualTo(1);
        assertThat(simulator.getDemandRunStatistics().failedPlanIds())
                .containsExactly("unreachable-return");

        simulator.reset();
        assertThat(simulator.getDemandRunStatistics().failedTrips()).isEqualTo(1);
    }

    private static RoadNetwork bidirectionalNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BA", "B", "A"));
        return network;
    }

    private static Road road(String id, String from, String to) {
        return new Road(
                id,
                from,
                to,
                0.001,
                50,
                20,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }

    private static RoutingServiceImpl routing() {
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        return new RoutingServiceImpl(registry);
    }

}
