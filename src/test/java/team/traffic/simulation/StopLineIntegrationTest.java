package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RouteStep;
import team.traffic.domain.signal.SignalPhase;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.domain.scene.LaneGeometryBuilder;
import team.traffic.domain.scene.StopLine;
import team.traffic.domain.vehicle.Bus;
import team.traffic.domain.network.BusStop;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.pose.PolylineGeometry;
import team.traffic.domain.vehicle.pose.VehiclePose;
import team.traffic.domain.vehicle.pose.VehiclePoseResolver;

class StopLineIntegrationTest {

    @Test
    void redSignalKeepsApproachingVehicleOnItsIncomingRoadUntilGreen() {
        RoadNetwork network = twoRoadNetwork();
        Car car = new Car("car", "A", "B");
        car.setCurrentRoute(route());
        TrafficSignal signal = new TrafficSignal("J", List.of(
                new SignalPhase(Set.of("OTHER"), 20.0),
                new SignalPhase(Set.of("IN"), 20.0)));
        VehicleDynamicsEngine engine = new VehicleDynamicsEngine();

        for (int second = 0; second < 18; second++) {
            engine.step(second, second, network, List.of(car), List.of(), List.of(signal), 1.0);
        }

        assertThat(car.getOccupiedRoadId()).isEqualTo("IN");
        assertThat(car.getCurrentIntersectionId()).isEqualTo("A");
        assertThat(car.getRoadProgress()).isGreaterThan(0.9);
        assertThat(car.getInstantaneousSpeedMps()).isLessThan(0.5);

        for (int second = 20; second < 36 && !car.hasReachedDestination(); second++) {
            engine.step(second, second, network, List.of(car), List.of(), List.of(signal), 1.0);
        }

        assertThat(car.hasReachedDestination()).isTrue();
    }

    @Test
    void redSignalLeavesTheWholeVehicleBehindThePaintedStopLine() {
        RoadNetwork network = geographicTwoRoadNetwork();
        Car car = new Car("car", "A", "B");
        car.setCurrentRoute(route());
        TrafficSignal signal = new TrafficSignal("J", List.of(
                new SignalPhase(Set.of("OTHER"), 20.0),
                new SignalPhase(Set.of("IN"), 20.0)));
        VehicleDynamicsEngine engine = new VehicleDynamicsEngine();

        for (int second = 0; second < 18; second++) {
            engine.step(second, second, network, List.of(car), List.of(), List.of(signal), 1.0);
        }

        VehiclePose pose = new VehiclePoseResolver().resolve(car, network).orElseThrow();
        StopLine line = new LaneGeometryBuilder(network).stopLineOf(network.getRoad("IN"));
        GeoPoint midpoint = new GeoPoint(
                (line.from().latitude() + line.to().latitude()) / 2.0,
                (line.from().longitude() + line.to().longitude()) / 2.0);
        double centreToLineMetres =
                PolylineGeometry.lengthMetres(List.of(pose.position(), midpoint));

        assertThat(car.getRoadProgress()).isGreaterThan(0.99);
        double frontBumperGapMetres = centreToLineMetres - car.getLength() / 2.0;
        assertThat(frontBumperGapMetres)
                .as("the entire body stays before the line, with at most a small braking margin")
                .isBetween(-0.01, 1.0);
        assertThat(pose.latitude()).isLessThan(midpoint.latitude());
    }

    @Test
    void busDwellsInItsLaneWhileFollowingCarCanUseTheOtherLane() {
        RoadNetwork network = twoRoadNetwork();
        Bus bus = new Bus("bus", "A", "B", 40);
        bus.setCurrentRoute(route());
        // Kerb-side stop near the far end of road "IN", where the junction-based
        // stop used to sit, so this still exercises dwelling beside moving traffic.
        bus.setStops(List.of(new BusStop("S1", "Stop", "IN", 0.98, 12.0)));
        Car car = new Car("car", "A", "B");
        car.setCurrentRoute(route());
        List<Vehicle> vehicles = new ArrayList<>();
        vehicles.add(bus);
        VehicleDynamicsEngine engine = new VehicleDynamicsEngine();

        for (int second = 0; second < 4; second++) {
            engine.step(second, second, network, vehicles, List.of(), 1.0);
        }
        vehicles.add(car);

        boolean observedPhysicalDwell = false;
        boolean carPassedDwellingBus = false;
        for (int second = 4; second < 60; second++) {
            engine.step(second, second, network, vehicles, List.of(), 1.0);
            // Keyed off the stop rather than a fixed progress value: the bus
            // halts a couple of metres short of the kerb, which on this 50 m
            // road is a whole 0.04 of progress.
            boolean busDwelling = "IN".equals(bus.getOccupiedRoadId())
                    && bus.getRoadProgress() > 0.9
                    && !bus.getServedStopIds().contains("S1")
                    && bus.getInstantaneousSpeedMps() < 0.5;
            if (busDwelling) {
                observedPhysicalDwell = true;
                carPassedDwellingBus |= !"IN".equals(car.getOccupiedRoadId())
                        || car.getLaneIndex() != bus.getLaneIndex()
                        || car.getRoadProgress() > bus.getRoadProgress();
            }
        }

        assertThat(observedPhysicalDwell).isTrue();
        assertThat(carPassedDwellingBus).isTrue();
        assertThat(bus.getServedStopIds()).contains("S1");
    }

    private static RoadNetwork twoRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("J", "J", 1, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 2, 0, LocationType.NORMAL));
        network.addRoad(road("IN", "A", "J"));
        network.addRoad(road("OUT", "J", "B"));
        return network;
    }

    private static RoadNetwork geographicTwoRoadNetwork() {
        RoadNetwork network = new RoadNetwork();
        GeoPoint a = new GeoPoint(10.000000, 106.000000);
        GeoPoint junction = new GeoPoint(10.000449, 106.000000);
        GeoPoint b = new GeoPoint(10.000898, 106.000000);
        network.addIntersection(new Intersection(
                "A", "A", 0, 0, LocationType.NORMAL, a.latitude(), a.longitude()));
        network.addIntersection(new Intersection(
                "J", "J", 0, 1, LocationType.NORMAL,
                junction.latitude(), junction.longitude()));
        network.addIntersection(new Intersection(
                "B", "B", 0, 2, LocationType.NORMAL, b.latitude(), b.longitude()));
        network.addRoad(road("IN", "A", "J", List.of(a, junction)));
        network.addRoad(road("OUT", "J", "B", List.of(junction, b)));
        return network;
    }

    private static Road road(String id, String from, String to) {
        return road(id, from, to, List.of());
    }

    private static Road road(
            String id, String from, String to, List<GeoPoint> geometry) {
        return new Road(id, id, id, from, to, 0.05, 40, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                geometry, RoadClass.SECONDARY);
    }

    private static Route route() {
        return new Route(List.of(
                new RouteStep("A", "J", "IN", 1.0),
                new RouteStep("J", "B", "OUT", 1.0)),
                0.1, 1.0, 1.0);
    }
}
