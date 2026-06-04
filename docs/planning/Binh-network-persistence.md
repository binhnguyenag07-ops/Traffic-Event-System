# Binh: Road Network and Persistence

This document contains Binh's part of the first documentation commit.
Binh is responsible for the road network model, road condition model, map loading, map saving, and synthetic map generation.

## Folder Structure

```text
src/main/java/team/traffic/domain/network/
|-- RoadNetwork.java
|-- Intersection.java
|-- Road.java
|-- RoadCondition.java
|-- RoadStatus.java
`-- LocationType.java

src/main/java/team/traffic/persistence/
|-- MapLoader.java
|-- CsvMapLoader.java
|-- JsonMapLoader.java
|-- MapSaver.java
`-- SyntheticMapGenerator.java

data/
|-- sample_map.csv
|-- sample_map.json
|-- small_city.json
|-- congestion_demo.json
`-- accident_demo.json
```

## Responsibilities

| Class | Responsibility |
|---|---|
| `RoadNetwork` | Stores the graph of intersections and roads, and provides graph operations. |
| `Intersection` | Represents a point in the city map. |
| `Road` | Represents a directed connection between two intersections. |
| `RoadCondition` | Stores congestion level, vehicle density, and current road status. |
| `RoadStatus` | Enum for road state: open, congested, blocked, or closed. |
| `LocationType` | Enum for realistic places such as school, hospital, bar, residential, and commercial areas. |
| `MapLoader` | Interface for loading a road network from a file. |
| `CsvMapLoader` | Loads road network data from CSV files. |
| `JsonMapLoader` | Loads road network data from JSON files. |
| `MapSaver` | Saves the current road network to a file. |
| `SyntheticMapGenerator` | Generates automatic demo maps for simulation and testing. |

## Class Diagram

```mermaid
classDiagram
    class RoadNetwork {
        -Map~String, Intersection~ intersections
        -Map~String, Road~ roads
        +addIntersection(Intersection)
        +removeIntersection(String)
        +addRoad(Road)
        +removeRoad(String)
        +findOutgoingRoads(String) List~Road~
        +findIncomingRoads(String) List~Road~
        +updateRoadCondition(String, RoadCondition)
    }

    class Intersection {
        -String id
        -String name
        -double x
        -double y
        -LocationType locationType
    }

    class Road {
        -String id
        -String startIntersectionId
        -String endIntersectionId
        -double distance
        -double speedLimit
        -int capacity
        -int currentVehicleCount
        -RoadCondition condition
        +calculateTravelCost() double
        +isAvailable() boolean
    }

    class RoadCondition {
        -double congestionLevel
        -double vehicleDensity
        -RoadStatus status
        +increaseCongestion(double)
        +markBlocked()
        +markOpen()
    }

    class RoadStatus {
        <<enumeration>>
        OPEN
        CONGESTED
        BLOCKED
        CLOSED
    }

    class LocationType {
        <<enumeration>>
        SCHOOL
        HOSPITAL
        BAR
        RESIDENTIAL
        COMMERCIAL
        NORMAL
    }

    class MapLoader {
        <<interface>>
        +load(String) RoadNetwork
    }

    class CsvMapLoader
    class JsonMapLoader
    class MapSaver
    class SyntheticMapGenerator {
        +generateGridMap(int, int) RoadNetwork
    }

    RoadNetwork "1" *-- "*" Intersection
    RoadNetwork "1" *-- "*" Road
    Road --> RoadCondition
    RoadCondition --> RoadStatus
    Intersection --> LocationType

    MapLoader <|.. CsvMapLoader
    MapLoader <|.. JsonMapLoader
    CsvMapLoader --> RoadNetwork
    JsonMapLoader --> RoadNetwork
    SyntheticMapGenerator --> RoadNetwork
```

