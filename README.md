# Traffic Event System

Urban traffic simulator — OOP project simulating roads, vehicles, routing algorithms, and traffic events.

## Tech Stack

| Component | Technology |
|---|---|
| Backend | Java 17, Maven, JDK HttpServer |
| JSON | Jackson 2.17 |
| Testing | JUnit 5 |
| Frontend | React 19, Vite 7, CSS |

## Backend

Pure Java backend with layered architecture:

- `domain`: road network, vehicles, events, statistics
- `routing`: BFS, Dijkstra, A* strategy contracts
- `simulation`: tick-based simulation engine
- `service`: application service interfaces + implementations
- `api`: HTTP controllers and route registry
- `persistence`: CSV/JSON map loading and synthetic generation
- `observer`: event notification system

### Build & Run

```bash
# Compile
mvn compile

# Run tests
mvn test

# Package fat JAR
mvn package

# Run
java -jar target/traffic-urban-simulator-1.0-SNAPSHOT.jar
```

## Frontend

React + Vite app with plain CSS. Dev server proxies `/api` to backend.

```bash
cd frontend
npm install
npm run dev
```

Open `http://localhost:5173` — API calls are proxied to `http://localhost:8080`.

## Sample Data

Sample road networks are in `data/`:
- `sample_map.csv` — CSV format
- `sample_map.json` — JSON format

## Documentation

See `docs/ARCHITECTURE.md` for full architecture, design patterns, task assignments, and timeline.
