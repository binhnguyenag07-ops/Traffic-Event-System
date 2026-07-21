package team.traffic.service.impl;

import java.util.NoSuchElementException;
import java.util.Objects;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.routing.PathFindingStrategy;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoutingService;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.routing.DijkstraPathFindingStrategy;
import java.util.LinkedHashMap;
import java.util.Map;

public class RoutingServiceImpl implements RoutingService {

    private final RoutingStrategyRegistry registry;
    private final Map<String, Metrics> metrics = new LinkedHashMap<>();

    public RoutingServiceImpl(RoutingStrategyRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "RoutingStrategyRegistry must not be null.");
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request) {
        Objects.requireNonNull(network, "RoadNetwork must not be null.");
        Objects.requireNonNull(request, "RouteRequest must not be null.");

        if (!network.containsIntersection(request.getStartIntersectionId())) {
            throw new IllegalArgumentException(
                    "Start intersection not found: " + request.getStartIntersectionId());
        }
        if (!network.containsIntersection(request.getDestinationIntersectionId())) {
            throw new IllegalArgumentException(
                    "Destination intersection not found: " + request.getDestinationIntersectionId());
        }

        RoutePreference preference = request.getPreference();
        PathFindingStrategy strategy = registry.findForPreference(preference)
                .orElseThrow(() -> new NoSuchElementException(
                        "No routing strategy found for preference: " + preference));

        return execute(strategy, network, request);
    }

    @Override
    public RouteResult findRoute(RoadNetwork network, RouteRequest request, String algorithm) {
        Objects.requireNonNull(network, "RoadNetwork must not be null.");
        Objects.requireNonNull(request, "RouteRequest must not be null.");
        if (algorithm == null || algorithm.isBlank()) {
            return findRoute(network, request);
        }
        PathFindingStrategy strategy = registry.findByName(algorithm)
                .orElseThrow(() -> new IllegalArgumentException("Unknown routing algorithm: " + algorithm));
        if (!strategy.supports(request.getPreference())) {
            throw new IllegalArgumentException(strategy.getName() + " does not support " + request.getPreference());
        }
        return execute(strategy, network, request);
    }

    @Override
    public java.util.List<PathFindingStrategy> getStrategies() {
        return registry.getAll();
    }

    @Override
    public synchronized java.util.List<AlgorithmStatistics> getAlgorithmStatistics() {
        return registry.getAll().stream().map(strategy -> {
            Metrics current = metrics.getOrDefault(strategy.getName(), new Metrics());
            AlgorithmStatistics result = new AlgorithmStatistics(strategy.getName(),
                    java.util.Arrays.stream(RoutePreference.values())
                            .filter(strategy::supports).map(Enum::name).toList());
            result.setTotalRoutesComputed(current.attempts);
            result.setSuccessfulRoutes(current.successes);
            result.setFailedRoutes(current.failures);
            result.setAverageComputationTime(current.successes == 0 ? 0 : current.totalNanos / 1_000_000.0 / current.successes);
            result.setAverageVisitedNodes(current.successes == 0 ? 0 : (double) current.totalVisited / current.successes);
            result.setAverageRouteCost(current.successes == 0 ? 0 : current.totalCost / current.successes);
            result.setAveragePathOptimality(current.successes == 0 ? 0 : current.totalOptimality / current.successes);
            result.setRouteRecalculationCount(current.recalculations);
            return result;
        }).toList();
    }

    @Override
    public synchronized void recordRouteRecalculation(String algorithmName) {
        if (algorithmName == null || algorithmName.isBlank()) {
            return;
        }
        metrics.computeIfAbsent(algorithmName, ignored -> new Metrics()).recalculations++;
    }

    private RouteResult execute(PathFindingStrategy strategy, RoadNetwork network, RouteRequest request) {
        long started = System.nanoTime();
        try {
            RouteResult result = strategy.findRoute(network, request);
            long elapsedNanos = System.nanoTime() - started;
            recordSuccess(strategy.getName(), elapsedNanos, result, calculateOptimality(strategy, network, request, result));
            return result;
        } catch (RuntimeException exception) {
            recordFailure(strategy.getName());
            throw exception;
        }
    }

    private synchronized void recordSuccess(String name, long nanos, RouteResult result, double optimality) {
        Metrics current = metrics.computeIfAbsent(name, ignored -> new Metrics());
        current.attempts++;
        current.successes++;
        current.totalNanos += nanos;
        current.totalVisited += result.getVisitedNodes();
        current.totalCost += result.getRoute().getTotalCost();
        current.totalOptimality += optimality;
    }

    private double calculateOptimality(
            PathFindingStrategy strategy,
            RoadNetwork network,
            RouteRequest request,
            RouteResult result) {
        if (!"A*".equals(strategy.getName())) {
            return 100.0;
        }
        try {
            RouteResult optimal = new DijkstraPathFindingStrategy().findRoute(network, request);
            double optimalCost = optimal.getRoute().getTotalCost();
            double actualCost = result.getRoute().getTotalCost();
            if (actualCost == 0) {
                return optimalCost == 0 ? 100.0 : 0.0;
            }
            return Math.max(0.0, Math.min(100.0, optimalCost / actualCost * 100.0));
        } catch (RuntimeException exception) {
            return 0.0;
        }
    }

    private synchronized void recordFailure(String name) {
        Metrics current = metrics.computeIfAbsent(name, ignored -> new Metrics());
        current.attempts++;
        current.failures++;
    }

    private static class Metrics {
        long attempts;
        long successes;
        long failures;
        long totalNanos;
        long totalVisited;
        double totalCost;
        double totalOptimality;
        int recalculations;
    }
}
