# Traffic Event System

An object-oriented urban traffic simulator project built with Java 17 and
Maven. The system models a directed road graph, loads map data from JSON or
CSV, generates synthetic grid maps, and provides BFS, Dijkstra, and A* routing
structures.

## Week 3 Status

The repository currently contains the Week 3 foundation:

- road-network graph operations;
- indexed incoming and outgoing roads;
- JSON and CSV map loading;
- deterministic JSON map saving;
- synthetic grid-map generation;
- road travel-cost calculation;
- BFS, Dijkstra, and A* routing modules;
- unit tests for network, persistence, and routing.

The simulation engine, API, complete frontend, traffic-event integration, and
statistics are later-roadmap work. Some source files for those modules are
currently placeholders and are not part of the Week 3 runnable scope.

## Requirements

- Java Development Kit 17 or newer
- Apache Maven 3.9 or newer
- Git

Check the installed versions:

```powershell
java -version
mvn -version
git --version
```

## Setup

```powershell
git clone https://github.com/binhnguyenag07-ops/Traffic-Event-System.git
cd Traffic-Event-System
mvn -DskipTests package
```

At Week 3, tests are the main executable verification because the application
entry point and HTTP API are not implemented yet.

## Run Student A Tests

To run only the road-network and persistence tests:

```powershell
mvn "-Dtest=team.traffic.domain.network.*Test,team.traffic.persistence.*Test" test
```

This command currently runs 50 network and persistence tests.

## Full Test Suite Status

Run every repository test with:

```powershell
mvn test
```

At the time of this Week 3 update, 175 of 177 tests pass. The remaining two
legacy tests are in `team.traffic.routing.RoadNetworkTest`; they still expect a
duplicate intersection or road ID to replace the existing object. The current
network contract deliberately rejects duplicate IDs to preserve graph
integrity. The routing owner should align those two test expectations with the
shared network contract. No routing production code needs to change for this
issue.

## Road Network Model

`RoadNetwork` stores directed roads and intersections. Each bidirectional
street is represented by two `Road` objects, one for each direction.

Important graph operations include:

- adding and removing intersections and roads;
- finding incoming and outgoing roads;
- finding outgoing neighboring intersections;
- updating road conditions;
- checking graph membership and counts.

Road and intersection IDs must be unique. Removing an intersection also
removes every connected road.

## Map Data

Available map files:

| File | Purpose |
|---|---|
| `data/sample_map.json` | Small JSON loader example |
| `data/sample_map.csv` | CSV equivalent of the sample map |
| `data/small_city.json` | Six-intersection routing scenario with alternative, congested, and blocked roads |
| `data/congestion_demo.json` | Reserved for the Week 5 event scenario |
| `data/accident_demo.json` | Reserved for the Week 5 event scenario |

The empty event files are roadmap placeholders and are not Week 3 map inputs.

### Units

| Field | Unit or range |
|---|---|
| `distance` | kilometres |
| `speedLimit` | kilometres per hour |
| travel time and routing cost | hours |
| `congestionLevel` | `0.0` to `1.0` |
| `vehicleDensity` | `0.0` to `1.0` |

Congestion-aware cost is:

```text
travelCost = distance / speedLimit
             * (1 + congestionLevel + vehicleDensity)
```

A blocked, closed, or full road has an infinite routing cost.

### JSON Structure

```json
{
  "intersections": [
    {
      "id": "I1",
      "name": "Campus Gate",
      "x": 0.0,
      "y": 0.0,
      "locationType": "NORMAL"
    }
  ],
  "roads": [
    {
      "id": "R1",
      "startIntersectionId": "I1",
      "endIntersectionId": "I2",
      "distance": 1.5,
      "speedLimit": 40.0,
      "capacity": 60,
      "currentVehicleCount": 12,
      "congestionLevel": 0.1,
      "vehicleDensity": 0.2,
      "roadStatus": "OPEN"
    }
  ]
}
```

Supported location types:

```text
SCHOOL, HOSPITAL, BAR, RESIDENTIAL, COMMERCIAL, NORMAL
```

Supported road statuses:

```text
OPEN, CONGESTED, BLOCKED, CLOSED
```

Invalid fields, duplicate IDs, missing endpoints, unsupported CSV records, and
empty maps are reported with `InvalidMapException`.

## Load and Save a Map

```java
MapLoader loader = new JsonMapLoader();
RoadNetwork network = loader.load("data/small_city.json");

MapSaver saver = new MapSaver();
saver.save(network, "output/saved-city.json");
```

For CSV:

```java
RoadNetwork network = new CsvMapLoader().load("data/sample_map.csv");
```

CSV `ROAD` records may appear before `INTERSECTION` records; the loader
resolves intersections before adding roads.

## Generate a Synthetic Map

```java
SyntheticMapGenerator generator = new SyntheticMapGenerator();
RoadNetwork grid = generator.generateGridMap(5, 5);
```

A grid contains:

```text
intersections = rows * columns
directed roads = 2 * [rows * (columns - 1) + columns * (rows - 1)]
```

Each neighboring pair receives two directed roads. Generated roads are 1 km
long, have a 50 km/h speed limit, capacity 100, and start open with no traffic.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Class diagram](docs/CLASS_DIAGRAM.md)
- [Student A network and persistence notes](docs/planning/Binh-network-persistence.md)
- [Routing planning notes](docs/planning/nguynie-routing-algorithms.md)
