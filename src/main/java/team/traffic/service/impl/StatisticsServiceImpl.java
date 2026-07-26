package team.traffic.service.impl;

import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.statistics.CompletedTrip;
import team.traffic.domain.statistics.TrafficStatistics;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.routing.RoutingStrategyRegistry;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;
import team.traffic.service.StatisticsService;
import team.traffic.service.RoutingService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StatisticsServiceImpl implements StatisticsService {
    private final SimulationService simulationService;
    private final RoadNetworkService networkService;
    private final RoutingStrategyRegistry strategyRegistry;
    private final RoutingService routingService;

    public StatisticsServiceImpl(
            SimulationService simulationService,
            RoadNetworkService networkService,
            RoutingStrategyRegistry strategyRegistry) {
        this(simulationService, networkService, strategyRegistry, null);
    }

    public StatisticsServiceImpl(
            SimulationService simulationService,
            RoadNetworkService networkService,
            RoutingStrategyRegistry strategyRegistry,
            RoutingService routingService) {
        this.simulationService = simulationService;
        this.networkService = networkService;
        this.strategyRegistry = strategyRegistry;
        this.routingService = routingService;
    }

    @Override
    public TrafficStatistics getTrafficStatistics() {
        TrafficStatistics stats = new TrafficStatistics();
        stats.setCurrentTick(simulationService.getCurrentTick());

        var vehicles = simulationService.getVehicles();
        List<CompletedTrip> archivedTrips = simulationService.getCompletedTrips();
        var arrivedVehicles = vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.ARRIVED).toList();
        stats.setTotalVehicles(vehicles.size());
        stats.setDwellingVehicles((int) vehicles.stream()
                .filter(v -> v.getStatus() == VehicleStatus.DWELLING).count());
        stats.setRoadActiveVehicles((int) vehicles.stream()
                .filter(v -> v.getStatus() != VehicleStatus.DWELLING
                        && v.getStatus() != VehicleStatus.ARRIVED).count());
        stats.setArrivedVehicles(archivedTrips.size() + arrivedVehicles.size());
        stats.setMovingVehicles((int) vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.MOVING).count());
        stats.setBlockedVehicles((int) vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.BLOCKED).count());
        stats.setWaitingVehicles((int) vehicles.stream().filter(v -> v.getStatus() == VehicleStatus.WAITING).count());
        stats.setActiveVehiclesByPurpose(vehicles.stream()
                .filter(vehicle -> vehicle.getTripPlan() != null)
                .filter(vehicle -> !vehicle.isJourneyComplete())
                .collect(Collectors.toMap(
                        vehicle -> vehicle.getTripPlan().purpose().value(),
                        vehicle -> 1,
                        Integer::sum)));
        Map<String, Integer> completedByPurpose = new HashMap<>();
        archivedTrips.stream()
                .map(CompletedTrip::tripPurpose)
                .filter(java.util.Objects::nonNull)
                .forEach(purpose -> completedByPurpose.merge(purpose, 1, Integer::sum));
        arrivedVehicles.stream()
                .filter(vehicle -> vehicle.getTripPlan() != null)
                .map(vehicle -> vehicle.getTripPlan().purpose().value())
                .forEach(purpose -> completedByPurpose.merge(purpose, 1, Integer::sum));
        stats.setCompletedTripsByPurpose(completedByPurpose);
        double totalTravelTime = archivedTrips.stream()
                .mapToDouble(CompletedTrip::travelTimeSeconds)
                .sum() + arrivedVehicles.stream()
                .mapToDouble(v -> v.getTravelTimeSeconds())
                .sum();
        double totalRouteLength = archivedTrips.stream()
                .mapToDouble(CompletedTrip::routeLengthKm)
                .sum() + arrivedVehicles.stream()
                .mapToDouble(v -> v.getDistanceTravelledKm())
                .sum();
        int completedTripCount = stats.getArrivedVehicles();
        stats.setTotalTravelTimeSeconds(totalTravelTime);
        stats.setAverageTravelTimeSeconds(completedTripCount == 0 ? 0 : totalTravelTime / completedTripCount);
        stats.setAverageRouteLengthKm(completedTripCount == 0 ? 0 : totalRouteLength / completedTripCount);

        RoadNetwork network = networkService.getNetwork();
        Collection<Road> roads = network.getRoads().values();

        double totalCongestion = roads.stream()
                .mapToDouble(r -> r.getCondition().getCongestionLevel())
                .sum();
        stats.setAverageCongestionLevel(roads.isEmpty() ? 0.0 : totalCongestion / roads.size());
        stats.setAverageVehicleDensity(roads.isEmpty() ? 0.0 : roads.stream()
                .mapToDouble(r -> r.getCondition().getVehicleDensity()).average().orElse(0.0));
        stats.setCongestedRoadCount((int) roads.stream()
                .filter(r -> r.getCondition().getRoadStatus() == RoadStatus.CONGESTED).count());
        stats.setBlockedRoadCount((int) roads.stream()
                .filter(r -> r.getCondition().getRoadStatus() == RoadStatus.BLOCKED).count());
        stats.setClosedRoadCount((int) roads.stream()
                .filter(r -> r.getCondition().getRoadStatus() == RoadStatus.CLOSED).count());

        stats.setActiveEventCount((int) simulationService.getEvents().stream()
                .filter(e -> e.getStatus() == TrafficEventStatus.ACTIVE).count());

        return stats;
    }

    @Override
    public List<AlgorithmStatistics> getAllAlgorithmStatistics() {
        if (routingService != null) {
            return routingService.getAlgorithmStatistics();
        }
        return strategyRegistry.getAll().stream()
                .map(strategy -> {
                    List<String> prefs = new ArrayList<>();
                    for (RoutePreference pref : RoutePreference.values()) {
                        if (strategy.supports(pref)) {
                            prefs.add(pref.name());
                        }
                    }
                    return new AlgorithmStatistics(strategy.getName(), prefs);
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Object> getFullStatistics() {
        Map<String, Object> result = new HashMap<>();
        result.put("traffic", getTrafficStatistics());
        result.put("algorithms", getAllAlgorithmStatistics());
        return result;
    }
}
