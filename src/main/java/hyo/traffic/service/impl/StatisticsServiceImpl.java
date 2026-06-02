package hyo.traffic.service.impl;

import hyo.traffic.domain.statistics.AlgorithmStatistics;
import hyo.traffic.domain.statistics.TrafficStatistics;
import hyo.traffic.service.StatisticsService;

import java.util.Map;

public class StatisticsServiceImpl implements StatisticsService {

    public StatisticsServiceImpl() {
        // No dependencies for now
    }

    @Override
    public TrafficStatistics calculateTrafficStatistics() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public AlgorithmStatistics calculateAlgorithmStatistics(String strategyName) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public Map<String, AlgorithmStatistics> compareAlgorithms() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void recordRouteComputation(String strategyName, long computationTimeMillis) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void recordVehicleArrival(String vehicleId, long travelTime, double routeLength) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
