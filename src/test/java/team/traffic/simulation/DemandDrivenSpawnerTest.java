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

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoutingServiceImpl;

class DemandDrivenSpawnerTest {

    @Test
    void saturatedEntryQueuesAStructurallyValidTripInsteadOfFailingDemand() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "Boundary entry", 0, 0));
        network.addIntersection(new Intersection("B", "District road", 1, 0));
        network.addRoad(new Road(
                "AB", "A", "B", 1, 40, 1, 1,
                new RoadCondition(1, 1, RoadStatus.CONGESTED)));
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new DijkstraPathFindingStrategy());
        TripPlan plan = new TripPlan(
                "cordon-queued",
                new PurposeId("INBOUND"),
                VehicleType.MOTORCYCLE,
                ResolvedPlace.intersection("A"),
                List.of(new TripLeg(ResolvedPlace.intersection("B"), 0)),
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                0);
        DemandDrivenSpawner spawner = new DemandDrivenSpawner(
                new ScriptedDemandModel(List.of(plan)),
                network,
                new RoutingServiceImpl(registry));

        var spawned = spawner.spawnDueThrough(0);

        assertThat(spawned).hasSize(1);
        assertThat(spawned.get(0).getStatus()).isEqualTo(VehicleStatus.WAITING);
        assertThat(spawner.statistics().spawnedTrips()).isOne();
        assertThat(spawner.statistics().failedTrips()).isZero();
    }
}
