# Traffic Event System

An object-oriented urban traffic simulator built with Java 17, Maven, Javalin,
and a React frontend. The system models a directed road network, loads scenario
maps, computes routes with BFS/Dijkstra/A*, runs multi-vehicle simulations, and
processes traffic events such as congestion, accidents, road closures, and
traffic-light delays.

## Current Status

The repository currently includes:

- road-network graph operations and density tracking;
- JSON/CSV map loading, map saving, and synthetic grid-map generation;
- route search with BFS, Dijkstra, A*, and congestion-aware route costs;
- vehicle models, route requests, route recomputation, and movement;
- traffic-event models for congestion, accident, road closure, and light delay;
- event service, event API, and dynamic rerouting support;
- simulation clock, tick processing, pause/resume/reset, and snapshots;
- traffic and algorithm statistics services;
- React frontend for map, controls, vehicles, events, heatmap, and statistics;
- unit and integration tests for domain, routing, persistence, services,
  controllers, and simulation.

## Requirements

- Java Development Kit 17 or newer
- Apache Maven 3.9 or newer
- Node.js and npm for the frontend
- Git

Check the installed versions:

```powershell
java -version
mvn -version
node -v
npm -v
git --version
```

## Backend Setup

```powershell
git clone https://github.com/binhnguyenag07-ops/Traffic-Event-System.git
cd Traffic-Event-System
mvn test
```

Run the API server:

```powershell
mvn exec:java
```

Default backend URL:

```text
http://localhost:7070
```

## Frontend Setup

```powershell
cd frontend
npm install
npm run dev
```

The Vite dev server will print the local frontend URL.

## Main API Endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | health check |
| `GET /api/network/intersections` | list intersections |
| `GET /api/network/roads` | list roads |
| `POST /api/routes` | compute route |
| `GET /api/vehicles` | list vehicles |
| `POST /api/vehicles` | create vehicle |
| `GET /api/events` | list events |
| `POST /api/events` | create traffic event |
| `GET /api/events/active` | list active events |
| `GET /api/events/road/{roadId}` | list events affecting one road |
| `PUT /api/events/{id}/status` | activate or resolve event |
| `DELETE /api/events/{id}` | remove event |
| `POST /api/simulation/initialize` | load simulation vehicles/events |
| `POST /api/simulation/start` | start simulation |
| `POST /api/simulation/pause` | pause simulation |
| `POST /api/simulation/resume` | resume simulation |
| `POST /api/simulation/reset` | reset simulation |
| `POST /api/simulation/tick` | advance one tick |
| `GET /api/simulation/snapshot` | current simulation snapshot |
| `GET /api/statistics` | combined statistics |
| `GET /api/statistics/traffic` | traffic statistics |
| `GET /api/statistics/algorithm/{name}` | algorithm statistics |

## Map Data

Available map files:

| File | Purpose |
|---|---|
| `data/sample_map.json` | small JSON loader example |
| `data/sample_map.csv` | CSV equivalent of the sample map |
| `data/small_city.json` | six-intersection routing scenario |
| `data/congestion_demo.json` | Week 6 congestion scenario |
| `data/accident_demo.json` | Week 6 accident scenario |
| `data/density_demo.json` | road-density scenario |

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

## Example Simulation Payload

```json
{
  "vehicles": [
    {
      "id": "car-1",
      "type": "CAR",
      "startIntersectionId": "I1",
      "destinationIntersectionId": "I6"
    }
  ],
  "events": [
    {
      "id": "event-1",
      "type": "ROAD_CLOSURE",
      "affectedRoadId": "R11",
      "startTick": 2,
      "durationTicks": 5
    }
  ]
}
```

Send it to:

```text
POST /api/simulation/initialize
```

Then call:

```text
POST /api/simulation/start
POST /api/simulation/tick
GET  /api/simulation/snapshot
```

## Test Suite

Run every backend test:

```powershell
mvn test
```

Run the frontend production build:

```powershell
cd frontend
npm run build
```

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Class diagram](docs/CLASS_DIAGRAM.md)
- [Planning overview](docs/PLANNING_OVERVIEW.md)
- [Student A network and persistence notes](docs/planning/Binh-network-persistence.md)
- [Student B routing notes](docs/planning/nguynie-routing-algorithms.md)
- [Student C vehicle and event notes](docs/planning/Hyo-vehicle-traffic_event.md)
- [Student D simulation/API/frontend notes](docs/planning/An-simulation-API-services-frontend.md)
