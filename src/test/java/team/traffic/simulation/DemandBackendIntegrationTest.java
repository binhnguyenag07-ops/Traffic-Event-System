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
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.JourneyPhase;
import team.traffic.domain.vehicle.pose.VehiclePoseResolver;
import team.traffic.domain.demand.PlaceKind;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.impl.RoutingServiceImpl;

class DemandBackendIntegrationTest {
    @Test
    void advancesBuildingAccessAtFifteenKphWithoutRoadOccupancyOrDoubleCountingTime() {
        RoadNetwork network = geographicBuildingNetwork(12.5, 8.0);
        TripPlan plan = new TripPlan(
                "driveway-progress",
                new PurposeId("COMMUTE"),
                VehicleType.CAR,
                ResolvedPlace.building("HOME", "A"),
                List.of(new TripLeg(ResolvedPlace.building("WORK", "B"), 0)),
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                0);
        TrafficSimulator simulator = new TrafficSimulator(network, routing());
        simulator.setSimulationSecondsPerTick(1);
        simulator.initialize(
                new ScriptedDemandModel(List.of(plan)),
                0,
                List.of(),
                List.of());

        var vehicle = simulator.getVehicles().get(0);
        assertThat(vehicle.isTraversingBuildingAccessLink()).isTrue();
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.DEPARTING_ORIGIN);
        assertThat(vehicle.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();

        simulator.advanceTick();

        assertThat(vehicle.getAccessLinkProgress()).isCloseTo(
                1.0 / 3.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(vehicle.getTravelTimeSeconds()).isCloseTo(
                1.0, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(vehicle.getDistanceTravelledKm()).isCloseTo(
                Vehicle.BUILDING_ACCESS_SPEED_MPS / 1_000.0,
                org.assertj.core.data.Offset.offset(1e-9));
        assertThat(vehicle.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("AB").getCurrentVehicleCount()).isZero();

        var pose = new VehiclePoseResolver().resolve(vehicle, network).orElseThrow();
        assertThat(pose.latitude()).isCloseTo(
                10.7699 + (10.7700 - 10.7699) / 3.0,
                org.assertj.core.data.Offset.offset(1e-8));
        assertThat(pose.roadId()).isNull();
    }

    @Test
    void completesDestinationAccessBeforeDwellAndDepartsFromThatBuildingOnReturn() {
        RoadNetwork network = geographicBuildingNetwork(
                Vehicle.BUILDING_ACCESS_SPEED_MPS,
                2 * Vehicle.BUILDING_ACCESS_SPEED_MPS);
        ResolvedPlace home = ResolvedPlace.building("HOME", "A");
        ResolvedPlace work = ResolvedPlace.building("WORK", "B");
        TripPlan plan = new TripPlan(
                "building-return",
                new PurposeId("COMMUTE"),
                VehicleType.MOTORCYCLE,
                home,
                List.of(
                        new TripLeg(work, 2),
                        new TripLeg(home, 0)),
                CompletionPolicy.RETURN_TO_ORIGIN,
                0);
        TrafficSimulator simulator = new TrafficSimulator(network, routing());
        simulator.setSimulationSecondsPerTick(1);
        simulator.initialize(
                new ScriptedDemandModel(List.of(plan)),
                0,
                List.of(),
                List.of());
        var vehicle = simulator.getVehicles().get(0);

        simulator.advanceTick(); // leave HOME and wait at access A
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.AT_JUNCTION);
        var departurePose = new VehiclePoseResolver()
                .resolveAll(List.of(vehicle), network)
                .get(vehicle.getId());
        assertThat(departurePose).isNotNull();
        assertThat(departurePose.latitude()).isCloseTo(
                10.7700, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(departurePose.longitude()).isCloseTo(
                106.7000, org.assertj.core.data.Offset.offset(1e-9));
        vehicle.moveTo("B");

        simulator.advanceTick();
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.ARRIVING_DESTINATION);
        assertThat(vehicle.getAccessLinkProgress()).isCloseTo(
                0.5, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(vehicle.getStatus()).isNotEqualTo(VehicleStatus.DWELLING);
        var arrivingPose = new VehiclePoseResolver()
                .resolveAll(List.of(vehicle), network)
                .get(vehicle.getId());
        assertThat(arrivingPose).isNotNull();
        assertThat(arrivingPose.latitude()).isCloseTo(
                10.77105, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(arrivingPose.longitude()).isCloseTo(
                106.7000, org.assertj.core.data.Offset.offset(1e-9));

        simulator.advanceTick();
        assertThat(vehicle.getStatus()).isEqualTo(VehicleStatus.DWELLING);
        assertThat(vehicle.getCurrentResolvedPlace()).isEqualTo(work);
        assertThat(simulator.getCurrentFrame().vehicles()).isEmpty();
        assertThat(simulator.getVehicleStatesAtTick(simulator.getCurrentTick()))
                .isEmpty();
        var dwellingPose = new VehiclePoseResolver()
                .resolveAll(List.of(vehicle), network)
                .get(vehicle.getId());
        assertThat(dwellingPose).isNotNull();
        assertThat(dwellingPose.latitude()).isCloseTo(
                10.7711, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(dwellingPose.longitude()).isCloseTo(
                106.7000, org.assertj.core.data.Offset.offset(1e-9));

        simulator.advanceTick();
        simulator.advanceTick();

        assertThat(vehicle.getCurrentTripLegIndex()).isEqualTo(1);
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.DEPARTING_ORIGIN);
        assertThat(vehicle.getOriginResolvedPlace()).isEqualTo(work);
        assertThat(vehicle.getDestinationResolvedPlace()).isEqualTo(home);
        assertThat(vehicle.getOccupiedRoadId()).isNull();
        assertThat(network.getRoad("BA").getCurrentVehicleCount()).isZero();

        simulator.advanceTick();
        simulator.advanceTick();
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.AT_JUNCTION);
        vehicle.moveTo("A");
        simulator.advanceTick();

        assertThat(vehicle.isJourneyComplete()).isTrue();
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.ARRIVED);
        var arrivedPose = new VehiclePoseResolver()
                .resolveAll(List.of(vehicle), network)
                .get(vehicle.getId());
        assertThat(arrivedPose).isNotNull();
        assertThat(arrivedPose.latitude()).isCloseTo(
                10.7699, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(arrivedPose.longitude()).isCloseTo(
                106.7000, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void preservesResolvedBuildingEndpointsAndPublishesAnOffNetworkDeparturePhase() {
        RoadNetwork network = bidirectionalNetwork();
        network.addBuilding(new Building(
                "HOME", "Home", BuildingType.RESIDENTIAL,
                0, 0, null, null,
                List.of(new BuildingAccessPoint("HOME-A", "A", 12.0))));
        network.addBuilding(new Building(
                "WORK", "Work", BuildingType.OFFICE,
                1, 0, null, null,
                List.of(new BuildingAccessPoint("WORK-B", "B", 8.0))));
        TripPlan plan = new TripPlan(
                "building-commute",
                new PurposeId("COMMUTE"),
                VehicleType.MOTORCYCLE,
                ResolvedPlace.building("HOME", "A"),
                List.of(new TripLeg(ResolvedPlace.building("WORK", "B"), 0)),
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                0);
        TrafficSimulator simulator = new TrafficSimulator(network, routing());

        simulator.initialize(
                new ScriptedDemandModel(List.of(plan)),
                6 * 3600,
                List.of(),
                List.of());

        var vehicle = simulator.getVehicles().get(0);
        assertThat(vehicle.getJourneyPhase()).isEqualTo(JourneyPhase.DEPARTING_ORIGIN);
        assertThat(vehicle.getOriginResolvedPlace().kind()).isEqualTo(PlaceKind.BUILDING);
        assertThat(vehicle.getOriginResolvedPlace().id()).isEqualTo("HOME");
        assertThat(vehicle.getOriginResolvedPlace().accessIntersectionId()).isEqualTo("A");
        assertThat(vehicle.getDestinationResolvedPlace().kind()).isEqualTo(PlaceKind.BUILDING);
        assertThat(vehicle.getDestinationResolvedPlace().id()).isEqualTo("WORK");
        assertThat(vehicle.getDestinationResolvedPlace().accessIntersectionId()).isEqualTo("B");
    }

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
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
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
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 1, 0));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BA", "B", "A"));
        return network;
    }

    private static RoadNetwork geographicBuildingNetwork(
            double homeDrivewayMetres,
            double workDrivewayMetres) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 106.7000, 10.7700, 10.7700, 106.7000));
        network.addIntersection(new Intersection("B", "B", 106.7000, 10.7710, 10.7710, 106.7000));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BA", "B", "A"));
        network.addBuilding(new Building(
                "HOME", "Home", BuildingType.RESIDENTIAL,
                0, 0, 10.7699, 106.7000,
                List.of(new BuildingAccessPoint(
                        "HOME-A", "A", homeDrivewayMetres))));
        network.addBuilding(new Building(
                "WORK", "Work", BuildingType.OFFICE,
                0, 1, 10.7711, 106.7000,
                List.of(new BuildingAccessPoint(
                        "WORK-B", "B", workDrivewayMetres))));
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
