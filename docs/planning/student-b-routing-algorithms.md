# Routing + Algorithms

This document contains the routing domain model and pathfinding algorithm structure for the Traffic Event System.

## Folder Structure

```text
src/main/java/team/traffic/domain/routing/
├── Route.java
├── RouteStep.java
├── RouteRequest.java
├── RouteResult.java
└── RoutePreference.java

src/main/java/team/traffic/routing/
├── PathFindingStrategy.java
├── BfsPathFindingStrategy.java
├── DijkstraPathFindingStrategy.java
├── AStarPathFindingStrategy.java
├── RoutingStrategyRegistry.java
└── Heuristic.java
```

## Class Diagram

```mermaid
classDiagram
    class Route {
        -List~RouteStep~ steps
        -double totalDistance
        -double estimatedTravelTime
        -double totalCost
    }

    class RouteStep {
        -String fromIntersectionId
        -String toIntersectionId
        -String roadId
        -double cost
    }

    class RouteRequest {
        -String startIntersectionId
        -String destinationIntersectionId
        -RoutePreference preference
    }

    class RouteResult {
        -Route route
        -String algorithmName
        -long computationTimeMillis
        -int visitedNodes
    }

    class RoutePreference {
        <<enumeration>>
        SHORTEST_DISTANCE
        SHORTEST_TIME
        CONGESTION_AWARE
    }

    class PathFindingStrategy {
        <<interface>>
        +getName() String
        +supports(RoutePreference) boolean
        +findRoute(RoadNetwork, RouteRequest) RouteResult
    }

    class BfsPathFindingStrategy
    class DijkstraPathFindingStrategy
    class AStarPathFindingStrategy

    class Heuristic {
        <<interface>>
        +estimate(Intersection, Intersection) double
    }

    class RoutingStrategyRegistry {
        -List~PathFindingStrategy~ strategies
        +register(PathFindingStrategy)
        +findByName(String) PathFindingStrategy
        +findForPreference(RoutePreference) PathFindingStrategy
    }

    Route "1" *-- "*" RouteStep
    RouteRequest --> RoutePreference
    RouteResult --> Route

    PathFindingStrategy <|.. BfsPathFindingStrategy
    PathFindingStrategy <|.. DijkstraPathFindingStrategy
    PathFindingStrategy <|.. AStarPathFindingStrategy
    AStarPathFindingStrategy --> Heuristic
    RoutingStrategyRegistry "1" *-- "*" PathFindingStrategy
```
