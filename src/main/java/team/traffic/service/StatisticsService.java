package team.traffic.service;

import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.statistics.TrafficStatistics;

import java.util.List;
import java.util.Map;

public interface StatisticsService {
    TrafficStatistics getTrafficStatistics();
    List<AlgorithmStatistics> getAllAlgorithmStatistics();
    Map<String, Object> getFullStatistics();
}
