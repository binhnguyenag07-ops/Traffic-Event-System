package team.traffic.routing;

import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

final class TestGraph {

    private TestGraph() {}

    // Builds a small graph for testing:
    //
    //   A ---roadAB(10)--> B ---roadBD(10)--> D
    //   |                                     ^
    //   +---roadAC(5)--> C ---roadCD(5)------+
    //
    // Also adds reverse roads so the graph is traversable both ways.
    static RoadNetwork buildSmallGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 10, 0));
        network.addIntersection(new Intersection("C", "C", 0, 5));
        network.addIntersection(new Intersection("D", "D", 10, 5));

        network.addRoad(road("AB", "A", "B", 10, 50));
        network.addRoad(road("BA", "B", "A", 10, 50));
        network.addRoad(road("BD", "B", "D", 10, 50));
        network.addRoad(road("DB", "D", "B", 10, 50));
        network.addRoad(road("AC", "A", "C", 5, 50));
        network.addRoad(road("CA", "C", "A", 5, 50));
        network.addRoad(road("CD", "C", "D", 5, 50));
        network.addRoad(road("DC", "D", "C", 5, 50));

        return network;
    }

    // Builds a graph where one path has high congestion.
    //
    //   A ---fast(10, speed=100)--> B ---fast(10, speed=100)--> D
    //   |                                                        ^
    //   +---slow(5, speed=10)--> C ---slow(5, speed=10)---------+
    //
    // For SHORTEST_DISTANCE: A->C->D = 10, A->B->D = 20
    // For SHORTEST_TIME:     A->B->D = 0.2, A->C->D = 1.0
    static RoadNetwork buildTimePreferenceGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 10, 0));
        network.addIntersection(new Intersection("C", "C", 0, 5));
        network.addIntersection(new Intersection("D", "D", 10, 5));

        network.addRoad(road("AB", "A", "B", 10, 100));
        network.addRoad(road("BA", "B", "A", 10, 100));
        network.addRoad(road("BD", "B", "D", 10, 100));
        network.addRoad(road("DB", "D", "B", 10, 100));
        network.addRoad(road("AC", "A", "C", 5, 10));
        network.addRoad(road("CA", "C", "A", 5, 10));
        network.addRoad(road("CD", "C", "D", 5, 10));
        network.addRoad(road("DC", "D", "C", 5, 10));

        return network;
    }

    // Builds a graph with a blocked road to test rerouting.
    //
    //   A ---roadAB(10)--> B ---roadBD(10)--> D
    //   |                                     ^
    //   +---roadAC(5)--> C ---roadCD(5)------+
    //
    // roadAB is BLOCKED, so only A->C->D works.
    static RoadNetwork buildBlockedRoadGraph() {
        RoadNetwork network = buildSmallGraph();

        Road blockedRoad = network.getRoads().get("AB");
        blockedRoad.setCondition(new RoadCondition(0, 0, RoadStatus.BLOCKED));

        return network;
    }

    // Builds a disconnected graph: A->B and C->D, no path between the two components.
    static RoadNetwork buildDisconnectedGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addIntersection(new Intersection("C", "C", 20, 0));
        network.addIntersection(new Intersection("D", "D", 25, 0));

        network.addRoad(road("AB", "A", "B", 5, 50));
        network.addRoad(road("CD", "C", "D", 5, 50));

        return network;
    }

    // Builds a single-node graph with no roads.
    static RoadNetwork buildSingleNodeGraph() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        return network;
    }

    // Builds a graph with a self-loop: A -> A.
    static RoadNetwork buildSelfLoopGraph() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addRoad(road("AA", "A", "A", 3, 50));
        network.addRoad(road("AB", "A", "B", 5, 50));
        return network;
    }

    // Builds a cyclic graph: A -> B -> C -> A.
    static RoadNetwork buildCyclicGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 10, 0));
        network.addIntersection(new Intersection("C", "C", 5, 8));

        network.addRoad(road("AB", "A", "B", 10, 50));
        network.addRoad(road("BC", "B", "C", 10, 50));
        network.addRoad(road("CA", "C", "A", 10, 50));

        return network;
    }

    // Builds a linear graph: A -> B -> C -> D (no alternative paths).
    static RoadNetwork buildLinearGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addIntersection(new Intersection("C", "C", 10, 0));
        network.addIntersection(new Intersection("D", "D", 15, 0));

        network.addRoad(road("AB", "A", "B", 5, 50));
        network.addRoad(road("BC", "B", "C", 5, 50));
        network.addRoad(road("CD", "C", "D", 5, 50));

        return network;
    }

    // Builds a graph with a triangle for cycle testing.
    //
    //   A ---10--> B
    //   |         |
    //   +---3---> C
    //
    static RoadNetwork buildTriangleGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 10, 0));
        network.addIntersection(new Intersection("C", "C", 5, 3));

        network.addRoad(road("AB", "A", "B", 10, 50));
        network.addRoad(road("AC", "A", "C", 3, 50));
        network.addRoad(road("CB", "C", "B", 7, 50));

        return network;
    }

    // Builds a graph where BFS (minimum hops) and Dijkstra (shortest distance) find different paths.
    //
    //   A ---long(100km, 1 hop)--> D
    //   |                          ^
    //   +---short(5km)--> B --short(5km)--+
    //
    // BFS finds A->D (1 hop, 100km)
    // Dijkstra finds A->B->D (2 hops, 10km)
    static RoadNetwork buildHopsVsDistanceGraph() {
        RoadNetwork network = new RoadNetwork();

        network.addIntersection(new Intersection("A", "A", 0, 0));
        network.addIntersection(new Intersection("B", "B", 5, 0));
        network.addIntersection(new Intersection("D", "D", 10, 0));

        network.addRoad(road("AD", "A", "D", 100, 50));
        network.addRoad(road("AB", "A", "B", 5, 50));
        network.addRoad(road("BD", "B", "D", 5, 50));

        return network;
    }

    private static Road road(String id, String from, String to, double distance, double speedLimit) {
        return new Road(id, from, to, distance, speedLimit, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
