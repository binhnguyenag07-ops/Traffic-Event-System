package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.observer.TrafficEventBus;
import team.traffic.persistence.JsonScenarioLoader;
import team.traffic.routing.AStarPathFindingStrategy;
import team.traffic.routing.BfsPathFindingStrategy;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoutingServiceImpl;

/** Regression for the real District 1 spillback shown around Công trường Mê Linh. */
class District1DeadlockRegressionTest {

    @Test
    void emptyContinuationsDoNotLeaveDistrictOneQueueHeadsStarved() throws Exception {
        JsonScenarioLoader.LoadedScenario scenario = new JsonScenarioLoader().load(
                Path.of("data/scenarios/district1_typical_day.json"));
        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new BfsPathFindingStrategy());
        registry.register(new DijkstraPathFindingStrategy());
        registry.register(new AStarPathFindingStrategy((left, right) -> Math.sqrt(
                Math.pow(left.getX() - right.getX(), 2)
                        + Math.pow(left.getY() - right.getY(), 2))));
        TrafficSimulator simulator = new TrafficSimulator(
                scenario.network(),
                new RoutingServiceImpl(registry),
                new TrafficEventBus(),
                AdaptiveTrafficConfig.defaults());
        simulator.configureAdaptiveTraffic(scenario.adaptiveTraffic());
        simulator.configureDriverDecisionProfiles(scenario.driverProfiles());
        simulator.initialize(
                scenario.demand().orElseThrow(),
                scenario.vehicles(),
                scenario.events());

        // The scenario is warm at T=300. T=1300 is beyond the frame where the
        // old model had queue heads waiting 1,000+ seconds for empty roads. A
        // one-second step also retains the real signal-cycle and entry-gap
        // behaviour that exposed size-based motorcycle barging at Me Linh.
        simulator.setSimulationSecondsPerTick(1.0);
        for (int tick = 0; tick < 1_000; tick++) {
            simulator.advanceTick();
        }

        double nowAt1300 = simulator.getElapsedSeconds();
        var active = simulator.getVehicles().stream()
                .filter(vehicle -> vehicle.getOccupiedRoadId() != null)
                .toList();
        var persistentFalseFull = active.stream()
                .filter(vehicle -> vehicle.getWaitState().cause() == WaitCause.DOWNSTREAM_FULL)
                .filter(vehicle -> nowAt1300 - vehicle.getWaitState().sinceSeconds() >= 120.0)
                .toList();

        assertThat(nowAt1300).isEqualTo(1_300.0);
        assertThat(persistentFalseFull)
                .as("no District 1 queue head may wait two minutes for a continuation")
                .isEmpty();
        assertThat(active.stream()
                .filter(vehicle -> "W326848006-0F".equals(vehicle.getOccupiedRoadId())))
                .as("the Hai Ba Trung approach that previously held 42 immobile vehicles")
                .hasSizeLessThan(10);
        assertThat(active.stream()
                .filter(vehicle -> "W193085683-2F".equals(vehicle.getOccupiedRoadId())))
                .as("buses must traverse the 10.5 m Ton Duc Thang connector")
                .isEmpty();

        // Continue through five more signal cycles. Before the lane-entry
        // reservation fix, cars at the head of W306072865-0F remained in place
        // while newer motorcycles repeatedly consumed every smaller gap.
        for (int tick = 0; tick < 300; tick++) {
            simulator.advanceTick();
        }
        double nowAt1600 = simulator.getElapsedSeconds();
        active = simulator.getVehicles().stream()
                .filter(vehicle -> vehicle.getOccupiedRoadId() != null)
                .toList();
        persistentFalseFull = active.stream()
                .filter(vehicle -> vehicle.getWaitState().cause() == WaitCause.DOWNSTREAM_FULL)
                .filter(vehicle -> nowAt1600 - vehicle.getWaitState().sinceSeconds() >= 120.0)
                .toList();

        assertThat(nowAt1600).isEqualTo(1_600.0);
        assertThat(persistentFalseFull)
                .as("small vehicles must not starve an older, larger queue head")
                .isEmpty();
    }
}
