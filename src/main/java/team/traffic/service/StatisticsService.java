package team.traffic.service;

import java.util.List;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.statistics.TrafficStatistics;

public interface StatisticsService {
    TrafficStatistics calculateTrafficStatistics();

    AlgorithmStatistics calculateAlgorithmStatistics(String strategyName);

    List<AlgorithmStatistics> compareAlgorithms();

    void recordRouteComputation(String strategyName, long computationTimeMillis);

    void recordVehicleArrival(String vehicleId, long travelTime, double routeLength);
}
