package team.traffic.experiment;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Random;
import java.nio.file.Files;
import java.nio.file.Path;
import com.fasterxml.jackson.databind.ObjectMapper;
import team.traffic.domain.event.CongestionEvent;
import team.traffic.domain.event.RoadClosureEvent;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.persistence.JsonMapLoader;
import team.traffic.persistence.SyntheticMapGenerator;
import team.traffic.routing.AStarPathFindingStrategy;
import team.traffic.routing.BfsPathFindingStrategy;
import team.traffic.routing.DijkstraPathFindingStrategy;
import team.traffic.routing.PathFindingStrategy;

public class ExperimentService {
    public static final long SEED = 42L;
    public static final int REQUESTS_PER_SCENARIO = 30;
    private final JsonMapLoader loader = new JsonMapLoader();
    private volatile ExperimentReport latest;

    public synchronized ExperimentReport getLatest() {
        if (latest == null) {
            Path baseline = Path.of("data/experiment_results.json");
            if (Files.isRegularFile(baseline)) {
                try {
                    latest = new ObjectMapper().readValue(baseline.toFile(), ExperimentReport.class);
                } catch (IOException ignored) {
                    // A stale baseline should not prevent a fresh experiment run.
                }
            }
        }
        return latest == null ? run() : latest;
    }

    public synchronized ExperimentReport run() {
        try {
            List<ScenarioInput> inputs = List.of(
                    new ScenarioInput("normal", "Normal traffic", loader.load("data/small_city.json")),
                    new ScenarioInput("heavy-congestion", "Heavy congestion", loader.load("data/congestion_demo.json")),
                    new ScenarioInput("random-events", "Seeded random events", randomEventNetwork()),
                    new ScenarioInput("large-network", "20×20 synthetic grid",
                            new SyntheticMapGenerator().generateGridMap(20, 20)));
            List<ScenarioResult> scenarios = new ArrayList<>();
            for (int index = 0; index < inputs.size(); index++) {
                scenarios.add(runScenario(inputs.get(index), SEED + index));
            }
            latest = new ExperimentReport(Instant.now().toString(), SEED, REQUESTS_PER_SCENARIO,
                    List.copyOf(scenarios));
            return latest;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load experiment maps: " + exception.getMessage(), exception);
        }
    }

    private RoadNetwork randomEventNetwork() throws IOException {
        RoadNetwork network = loader.load("data/small_city.json");
        List<Road> roads = new ArrayList<>(network.getRoads().values());
        Collections.shuffle(roads, new Random(SEED));
        int eventCount = Math.max(1, roads.size() / 4);
        for (int index = 0; index < eventCount; index++) {
            Road road = roads.get(index);
            if (index % 4 == 0) {
                new RoadClosureEvent("random-close-" + index, road.getId(), 0, 10).apply(network);
            } else {
                new CongestionEvent("random-congestion-" + index, road.getId(), 0, 10, 0.65).apply(network);
            }
        }
        return network;
    }

    private ScenarioResult runScenario(ScenarioInput input, long seed) {
        List<RoutePair> pairs = routePairs(input.network(), seed);
        List<AlgorithmResult> algorithms = List.of(
                benchmark(new BfsPathFindingStrategy(), RoutePreference.MINIMUM_HOPS, input.network(), pairs),
                benchmark(new DijkstraPathFindingStrategy(), RoutePreference.SHORTEST_TIME, input.network(), pairs),
                benchmark(new AStarPathFindingStrategy(this::straightLineDistance),
                        RoutePreference.SHORTEST_TIME, input.network(), pairs));
        return new ScenarioResult(input.id(), input.name(), input.network().getIntersectionCount(),
                input.network().getRoadCount(), algorithms);
    }

    private AlgorithmResult benchmark(
            PathFindingStrategy strategy,
            RoutePreference preference,
            RoadNetwork network,
            List<RoutePair> pairs) {
        int successes = 0;
        long totalNanos = 0;
        long totalVisited = 0;
        double totalCost = 0;
        double totalOptimality = 0;
        DijkstraPathFindingStrategy oracle = new DijkstraPathFindingStrategy();

        for (RoutePair pair : pairs) {
            RouteRequest request = new RouteRequest(pair.start(), pair.destination(), preference);
            long started = System.nanoTime();
            try {
                RouteResult result = strategy.findRoute(network, request);
                totalNanos += System.nanoTime() - started;
                successes++;
                totalVisited += result.getVisitedNodes();
                totalCost += result.getRoute().getTotalCost();
                if (strategy instanceof AStarPathFindingStrategy) {
                    RouteResult optimal = oracle.findRoute(network, request);
                    double actualCost = result.getRoute().getTotalCost();
                    totalOptimality += actualCost == 0 ? 100
                            : Math.max(0, Math.min(100, optimal.getRoute().getTotalCost() / actualCost * 100));
                } else {
                    totalOptimality += 100;
                }
            } catch (NoSuchElementException exception) {
                totalNanos += System.nanoTime() - started;
            }
        }
        int requests = pairs.size();
        return new AlgorithmResult(strategy.getName(), preference.name(), requests, successes,
                requests - successes, requests == 0 ? 0 : successes * 100.0 / requests,
                successes == 0 ? 0 : totalNanos / 1_000_000.0 / successes,
                successes == 0 ? 0 : (double) totalVisited / successes,
                successes == 0 ? 0 : totalCost / successes,
                successes == 0 ? 0 : totalOptimality / successes);
    }

    private List<RoutePair> routePairs(RoadNetwork network, long seed) {
        List<String> ids = new ArrayList<>(network.getIntersections().keySet());
        Random random = new Random(seed);
        List<RoutePair> result = new ArrayList<>();
        while (result.size() < REQUESTS_PER_SCENARIO && ids.size() > 1) {
            String start = ids.get(random.nextInt(ids.size()));
            String destination = ids.get(random.nextInt(ids.size()));
            if (!start.equals(destination)) {
                result.add(new RoutePair(start, destination));
            }
        }
        return List.copyOf(result);
    }

    private double straightLineDistance(Intersection from, Intersection to) {
        double dx = from.getX() - to.getX();
        double dy = from.getY() - to.getY();
        return Math.sqrt(dx * dx + dy * dy);
    }

    private record ScenarioInput(String id, String name, RoadNetwork network) {}
    private record RoutePair(String start, String destination) {}

    public record ExperimentReport(String generatedAt, long seed, int requestsPerScenario,
            List<ScenarioResult> scenarios) {}

    public record ScenarioResult(String id, String name, int intersectionCount, int roadCount,
            List<AlgorithmResult> algorithms) {}

    public record AlgorithmResult(String algorithm, String objective, int requests, int successes, int failures,
            double successRate, double averageComputationTimeMillis, double averageVisitedNodes,
            double averageRouteCost, double averagePathOptimality) {}
}
