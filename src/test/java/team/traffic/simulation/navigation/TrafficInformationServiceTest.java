package team.traffic.simulation.navigation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.dynamics.DriverProfile;
import team.traffic.domain.vehicle.navigation.AdvisoryKind;
import team.traffic.domain.vehicle.navigation.DriverDecisionProfile;
import team.traffic.domain.vehicle.navigation.InformationAccess;
import team.traffic.domain.vehicle.navigation.UTurnPolicy;
import team.traffic.routing.RoutingSnapshot;

class TrafficInformationServiceTest {

    @Test
    void scheduledEventDoesNotLeakAndClearanceHasItsOwnLatency() {
        RoadNetwork network = network();
        Car vehicle = vehicle("car", 5.0, 1.0);
        RoadClosureEvent event = new RoadClosureEvent(
                "closure", "AB", 100.0, 50.0);
        TrafficInformationService service =
                new TrafficInformationService(17L, 8);

        service.deliverDue(vehicle, network, List.of(event), 100.0);
        assertThat(vehicle.getDriverKnowledge().getVersion()).isZero();

        event.apply(network);
        service.deliverDue(vehicle, network, List.of(event), 104.999);
        assertThat(vehicle.getDriverKnowledge().getVersion()).isZero();

        service.deliverDue(vehicle, network, List.of(event), 105.0);
        assertThat(vehicle.getDriverKnowledge().activeAt(105.0))
                .extracting(advisory -> advisory.kind())
                .containsExactly(AdvisoryKind.CLOSED);

        event.resolve(network);
        service.deliverDue(vehicle, network, List.of(event), 154.999);
        assertThat(vehicle.getDriverKnowledge().activeAt(154.999))
                .isEmpty();

        service.deliverDue(vehicle, network, List.of(event), 155.0);
        assertThat(vehicle.getDriverKnowledge().activeAt(155.0))
                .extracting(advisory -> advisory.kind())
                .containsExactly(AdvisoryKind.CLEARED);
        assertThat(service.metrics().delivered()).isEqualTo(2);
        assertThat(service.metrics().meanDeliveryLatencySeconds())
                .isEqualTo(5.0);
    }

    @Test
    void receptionDecisionIsStableForTheSameSeedAndVehicleId() {
        RoadNetwork network = network();
        RoadClosureEvent event = new RoadClosureEvent(
                "closure", "AB", 10.0, 50.0);
        event.apply(network);
        Car firstVehicle = vehicle("same-id", 0.0, 0.5);
        Car replayVehicle = vehicle("same-id", 0.0, 0.5);
        TrafficInformationService first =
                new TrafficInformationService(99L, 8);
        TrafficInformationService replay =
                new TrafficInformationService(99L, 8);

        first.deliverDue(firstVehicle, network, List.of(event), 10.0);
        replay.deliverDue(replayVehicle, network, List.of(event), 10.0);

        assertThat(replay.metrics()).isEqualTo(first.metrics());
        assertThat(replayVehicle.getDriverKnowledge().getVersion())
                .isEqualTo(firstVehicle.getDriverKnowledge().getVersion());
    }

    @Test
    void measuredCongestionIsDeliveredOnlyThroughDriverInformationAccess() {
        RoadNetwork network = network();
        RoutingSnapshot truth = new RoutingSnapshot(
                0.0,
                Map.of("AB", new RoutingSnapshot.RoadState(
                        200.0, 0.8, true, false, false, 0)));
        Car connected = vehicle(
                "connected", 5.0, 1.0,
                InformationAccess.CONNECTED_NAVIGATION);
        Car localOnly = vehicle(
                "local", 5.0, 1.0,
                InformationAccess.LOCAL_ONLY);
        TrafficInformationService service =
                new TrafficInformationService(7L, 8, 10.0);
        service.updateCongestion(network, truth, 0.0);

        service.deliverDue(connected, network, List.of(), 4.999);
        service.deliverDue(localOnly, network, List.of(), 5.0);
        assertThat(connected.getDriverKnowledge().activeAt(4.999)).isEmpty();
        assertThat(localOnly.getDriverKnowledge().activeAt(5.0)).isEmpty();

        service.deliverDue(connected, network, List.of(), 5.0);

        assertThat(connected.getDriverKnowledge().activeAt(5.0))
                .extracting(advisory -> advisory.kind())
                .containsExactly(AdvisoryKind.CONGESTED);
        assertThat(truth.visibleTo(
                network,
                localOnly.getDriverKnowledge(),
                null,
                5.0).road("AB").travelTimeSeconds())
                .isEqualTo(network.getRoad("AB").getFreeFlowTravelTimeSeconds());
        assertThat(truth.visibleTo(
                network,
                connected.getDriverKnowledge(),
                null,
                5.0).road("AB").travelTimeSeconds())
                .isEqualTo(200.0);
    }

    private static Car vehicle(
            String id,
            double latencySeconds,
            double receptionProbability) {
        return vehicle(
                id,
                latencySeconds,
                receptionProbability,
                InformationAccess.CONNECTED_NAVIGATION);
    }

    private static Car vehicle(
            String id,
            double latencySeconds,
            double receptionProbability,
            InformationAccess informationAccess) {
        Car vehicle = new Car(id, "A", "B");
        vehicle.setCurrentRoute(new Route(
                List.of(new RouteStep("A", "B", "AB", 1.0)),
                1.0,
                0.02,
                1.0));
        DriverProfile motion = vehicle.getDriverProfile();
        vehicle.setDriverProfile(new DriverProfile(
                motion.minimumGapMetres(),
                motion.reactionTimeSeconds(),
                motion.imperfection(),
                motion.gapAcceptanceSeconds(),
                motion.speedFactor(),
                motion.lateralAccelerationMps2(),
                motion.politeness(),
                motion.maxLateralSpeedMps(),
                new DriverDecisionProfile(
                        informationAccess,
                        300.0,
                        latencySeconds,
                        receptionProbability,
                        10.0,
                        5.0,
                        20.0,
                        30.0,
                        UTurnPolicy.NEVER)));
        return vehicle;
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection(
                "A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection(
                "B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road(
                "AB",
                "A",
                "B",
                1.0,
                50.0,
                20,
                0,
                new RoadCondition(0.0, 0.0, RoadStatus.OPEN)));
        return network;
    }
}
