package team.traffic.routing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.vehicle.Car;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleFactory;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.observer.TrafficEventBus;
import team.traffic.service.RoutingService;
import team.traffic.service.impl.RoutingServiceImpl;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DynamicReroutingListenerTest {

    private RoadNetwork network;
    private RoutingService routingService;
    private VehicleFactory vehicleFactory;
    private TrafficEventBus eventBus;

    //   A ---AB(10)--> B ---BD(10)--> D
    //   |                                  ^
    //   +---AC(5)---> C ---CD(5)---------+
    //
    //   AB blocked => only A->C->D works

    @BeforeEach
    void setUp() {
        network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 10, 0));
        network.addIntersection(new Intersection("C", "C", 0, 5));
        network.addIntersection(new Intersection("D", "D", 10, 5));

        network.addRoad(new Road("AB", "A", "B", 10, 100, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("BA", "B", "A", 10, 100, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("BD", "B", "D", 10, 100, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("DB", "D", "B", 10, 100, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("AC", "A", "C", 5, 10, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("CA", "C", "A", 5, 10, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("CD", "C", "D", 5, 10, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        network.addRoad(new Road("DC", "D", "C", 5, 10, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
        registry.register(new BfsPathFindingStrategy());
        registry.register(new DijkstraPathFindingStrategy());
        registry.register(new AStarPathFindingStrategy((a, b) -> {
            double dx = a.getX() - b.getX();
            double dy = a.getY() - b.getY();
            return Math.sqrt(dx * dx + dy * dy);
        }));
        routingService = new RoutingServiceImpl(registry);

        vehicleFactory = new VehicleFactory();
        eventBus = new TrafficEventBus();
    }

    @Nested
    @DisplayName("accident blocks road")
    class AccidentBlockTests {

        @Test
        @DisplayName("vehicle on blocked road reroutes successfully")
        void vehicleOnBlockedRoadReroutes() {
            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            // Car is on A->B->D path (fast road, speed=100)
            assertThat(car.getCurrentRoute()).isNotNull();
            assertThat(car.getCurrentRoute().isEmpty()).isFalse();

            List<Vehicle> vehicles = new ArrayList<>();
            vehicles.add(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            AccidentEvent accident = new AccidentEvent("acc1", "AB", 0, 100, 1.0, "other-car");
            accident.apply(network);

            eventBus.publish(accident);

            assertThat(car.getStatus()).isNotEqualTo(VehicleStatus.BLOCKED);
            assertThat(car.getCurrentRoute()).isNotNull();
            assertThat(listener.getReroutingCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("vehicle not on blocked road is not rerouted")
        void vehicleNotOnRoadIsNotRerouted() {
            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, routingService, RoutePreference.MINIMUM_HOPS);

            List<Vehicle> vehicles = new ArrayList<>();
            vehicles.add(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            AccidentEvent accident = new AccidentEvent("acc1", "AC", 0, 100, 1.0, "other-car");
            accident.apply(network);

            eventBus.publish(accident);

            assertThat(listener.getReroutingCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("road closure reroutes vehicles")
    class RoadClosureTests {

        @Test
        @DisplayName("vehicle reroutes around closed road")
        void reroutesAroundClosedRoad() {
            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            List<Vehicle> vehicles = new ArrayList<>();
            vehicles.add(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            RoadClosureEvent closure = new RoadClosureEvent("close1", "AB", 0, 100);
            closure.apply(network);

            eventBus.publish(closure);

            assertThat(listener.getReroutingCount()).isEqualTo(1);
            assertThat(car.getCurrentRoute()).isNotNull();
        }
    }

    @Nested
    @DisplayName("conestion triggers reroute")
    class CongestionTests {

        @Test
        @DisplayName("vehicle on congested road reroutes")
        void reroutesOnCongestion() {
            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, routingService, RoutePreference.CONGESTION_AWARE);

            List<Vehicle> vehicles = new ArrayList<>();
            vehicles.add(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            CongestionEvent congestion = new CongestionEvent("cong1", "AB", 0, 100, 0.9);
            congestion.apply(network);

            eventBus.publish(congestion);

            assertThat(listener.getReroutingCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("blocked vehicle reroutes")
    class BlockedVehicleTests {

        @Test
        @DisplayName("blocked vehicle is rerouted even without matching road in route")
        void blockedVehicleReroutes() {
            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            car.setStatus(VehicleStatus.BLOCKED);

            List<Vehicle> vehicles = new ArrayList<>();
            vehicles.add(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            TrafficEvent event = new CongestionEvent("evt1", "BD", 0, 100, 0.5);
            event.apply(network);

            eventBus.publish(event);

            assertThat(listener.getReroutingCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("arrived vehicle not rerouted")
    class ArrivedVehicleTests {

        @Test
        @DisplayName("arrived vehicle is skipped")
        void arrivedVehicleSkipped() {
            Car car = vehicleFactory.createCar("car1", "A", "A");

            List<Vehicle> vehicles = new ArrayList<>();
            vehicles.add(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            TrafficEvent event = new CongestionEvent("evt1", "AB", 0, 100, 0.5);
            event.apply(network);

            eventBus.publish(event);

            assertThat(listener.getReroutingCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("multiple vehicles")
    class MultipleVehicleTests {

        @Test
        @DisplayName("all vehicles on affected road are rerouted")
        void allAffectedVehiclesRerouted() {
            Car car1 = vehicleFactory.createCar("car1", "A", "D");
            Car car2 = vehicleFactory.createCar("car2", "A", "D");
            car1.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);
            car2.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            List<Vehicle> vehicles = List.of(car1, car2);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            AccidentEvent accident = new AccidentEvent("acc1", "AB", 0, 100, 1.0, "other");
            accident.apply(network);

            eventBus.publish(accident);

            assertThat(listener.getReroutingCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("only affected vehicles are rerouted, not all")
        void onlyAffectedRerouted() {
            Car onAB = vehicleFactory.createCar("car1", "A", "D");
            onAB.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            Car onCD = vehicleFactory.createCar("car2", "C", "D");
            onCD.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            List<Vehicle> vehicles = List.of(onAB, onCD);
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            AccidentEvent accident = new AccidentEvent("acc1", "AB", 0, 100, 1.0, "other");
            accident.apply(network);

            eventBus.publish(accident);

            assertThat(listener.getReroutingCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("no alternative route")
    class NoAlternativeTests {

        @Test
        @DisplayName("vehicle stays blocked when no alternative exists")
        void staysBlockedWhenNoAlternative() {
            RoadNetwork noAlternative = new RoadNetwork();
            noAlternative.addIntersection(new Intersection("A", "A", 0, 0));
            noAlternative.addIntersection(new Intersection("B", "B", 10, 0));

            noAlternative.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0, 0, RoadStatus.OPEN)));

            RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
            registry.register(new DijkstraPathFindingStrategy());
            RoutingService simpleService = new RoutingServiceImpl(registry);

            Car car = vehicleFactory.createCar("car1", "A", "B");
            car.requestRoute(noAlternative, simpleService, RoutePreference.SHORTEST_DISTANCE);
            assertThat(car.getStatus()).isNotEqualTo(VehicleStatus.BLOCKED);

            List<Vehicle> vehicles = List.of(car);
            DynamicReroutingListener listener = new DynamicReroutingListener(noAlternative, simpleService, vehicles);

            AccidentEvent accident = new AccidentEvent("acc1", "AB", 0, 100, 1.0, "other");
            accident.apply(noAlternative);

            listener.onTrafficEvent(accident);

            assertThat(listener.getReroutingCount()).isEqualTo(1);
            assertThat(car.getStatus()).isEqualTo(VehicleStatus.BLOCKED);
        }
    }

    @Nested
    @DisplayName("event bus integration")
    class EventBusTests {

        @Test
        @DisplayName("listener receives event via bus")
        void receivesEventViaBus() {
            List<Vehicle> vehicles = List.of();
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);

            assertThat(eventBus.getListeners()).contains(listener);
        }

        @Test
        @DisplayName("unsubscribed listener does not receive events")
        void unsubscribedListenerSkipped() {
            List<Vehicle> vehicles = List.of();
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);
            eventBus.subscribe(listener);
            eventBus.unsubscribe(listener);

            TrafficEvent event = new CongestionEvent("evt1", "AB", 0, 100, 0.5);
            event.apply(network);

            eventBus.publish(event);

            assertThat(listener.getReroutingCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("getters")
    class GetterTests {

        @Test
        @DisplayName("getVehicles returns defensive copy")
        void getVehiclesReturnsCopy() {
            List<Vehicle> vehicles = new ArrayList<>();
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);

            List<Vehicle> copy = listener.getVehicles();
            assertThat(copy).isEmpty();

            Car car = vehicleFactory.createCar("X", "A", "B");
            vehicles.add(car);

            assertThat(listener.getVehicles()).isEmpty();
        }

        @Test
        @DisplayName("getReroutingCount starts at zero")
        void reroutingCountStartsAtZero() {
            List<Vehicle> vehicles = List.of();
            DynamicReroutingListener listener = new DynamicReroutingListener(network, routingService, vehicles);

            assertThat(listener.getReroutingCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("congestion avoidance")
    class CongestionAvoidanceTests {

        @Test
        @DisplayName("routing avoids roads above congestion threshold")
        void routingAvoidsHighCongestionRoads() {
            network.addRoad(new Road("AB2", "A", "B", 5, 100, 100, 0,
                    new RoadCondition(0.9, 0, RoadStatus.CONGESTED)));

            RoutingStrategyRegistry highThresholdRegistry = new RoutingStrategyRegistry();
            highThresholdRegistry.register(new DijkstraPathFindingStrategy(0.8));
            RoutingService highThresholdService = new RoutingServiceImpl(highThresholdRegistry);

            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, highThresholdService, RoutePreference.SHORTEST_TIME);

            assertThat(car.getCurrentRoute()).isNotNull();
            List<String> roadIds = car.getCurrentRoute().getSteps().stream()
                    .map(step -> step.getRoadId())
                    .toList();
            assertThat(roadIds).doesNotContain("AB2");
        }

        @Test
        @DisplayName("routing uses congested road when no alternative exists")
        void routingUsesCongestedRoadWhenNoAlternative() {
            RoadNetwork simpleNetwork = new RoadNetwork();
            simpleNetwork.addIntersection(new Intersection("A", "A", 0, 0));
            simpleNetwork.addIntersection(new Intersection("B", "B", 10, 0));

            simpleNetwork.addRoad(new Road("AB", "A", "B", 10, 50, 100, 0,
                    new RoadCondition(0.9, 0, RoadStatus.CONGESTED)));

            RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
            registry.register(new DijkstraPathFindingStrategy(0.95));
            RoutingService service = new RoutingServiceImpl(registry);

            Car car = vehicleFactory.createCar("car1", "A", "B");
            car.requestRoute(simpleNetwork, service, RoutePreference.SHORTEST_DISTANCE);

            assertThat(car.getCurrentRoute()).isNotNull();
            assertThat(car.getCurrentRoute().isEmpty()).isFalse();
        }

        @Test
        @DisplayName("default threshold avoids roads above 0.8 congestion")
        void defaultThresholdAvoidsHighCongestion() {
            network.addRoad(new Road("AB2", "A", "B", 5, 100, 100, 0,
                    new RoadCondition(0.9, 0, RoadStatus.CONGESTED)));

            Car car = vehicleFactory.createCar("car1", "A", "D");
            car.requestRoute(network, routingService, RoutePreference.SHORTEST_TIME);

            assertThat(car.getCurrentRoute()).isNotNull();
            List<String> roadIds = car.getCurrentRoute().getSteps().stream()
                    .map(step -> step.getRoadId())
                    .toList();
            assertThat(roadIds).doesNotContain("AB2");
        }
    }
}
