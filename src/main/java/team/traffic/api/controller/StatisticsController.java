package team.traffic.api.controller;

import java.util.List;
import team.traffic.api.dto.ApiResponse;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.domain.statistics.TrafficStatistics;
import team.traffic.service.StatisticsService;

public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    public ApiResponse<TrafficStatistics> getTrafficStatistics() {
        return ApiResponse.success(statisticsService.calculateTrafficStatistics());
    }

    public ApiResponse<AlgorithmStatistics> getAlgorithmStatistics(String strategyName) {
        return ApiResponse.success(statisticsService.calculateAlgorithmStatistics(strategyName));
    }

    public ApiResponse<List<AlgorithmStatistics>> compareAlgorithms() {
        return ApiResponse.success(statisticsService.compareAlgorithms());
    }
}
