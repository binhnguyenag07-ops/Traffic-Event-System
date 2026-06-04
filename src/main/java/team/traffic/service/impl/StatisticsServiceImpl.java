package team.traffic.service.impl;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.statistics.TrafficStatistics;
import team.traffic.service.StatisticsService;

public class StatisticsServiceImpl implements StatisticsService {
    @Override
    public TrafficStatistics calculateTrafficStatistics() {
        // TODO: calculate metrics from RoadNetwork and VehicleService.
        return new TrafficStatistics();
    }

    @Override
    public AlgorithmStatistics calculateAlgorithmStatistics(String strategyName) {
        // TODO: aggregate routing computation metrics per strategy.
        AlgorithmStatistics statistics = new AlgorithmStatistics();
        statistics.setStrategyName(strategyName);
        return statistics;
    }

    @Override
    public List<AlgorithmStatistics> compareAlgorithms() {
        // TODO: run/collect benchmark data across registered algorithms.
        return new ArrayList<>();
    }

    @Override
    public void recordRouteComputation(String strategyName, long computationTimeMillis) {
        // TODO: persist route computation metric.
    }

    @Override
    public void recordVehicleArrival(String vehicleId, long travelTime, double routeLength) {
        // TODO: persist vehicle arrival metric.
    }
}
