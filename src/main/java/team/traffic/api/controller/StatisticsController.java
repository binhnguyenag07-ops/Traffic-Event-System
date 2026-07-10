package team.traffic.api.controller;

import io.javalin.Javalin;
import team.traffic.domain.statistics.AlgorithmStatistics;
import team.traffic.service.StatisticsService;

import java.util.Map;
import java.util.Optional;

public class StatisticsController {
    private final StatisticsService statisticsService;

    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/statistics", ctx -> ctx.json(statisticsService.getFullStatistics()));
        app.get("/api/statistics/traffic", ctx -> ctx.json(statisticsService.getTrafficStatistics()));
        app.get("/api/statistics/algorithm/{name}", ctx -> {
            String name = ctx.pathParam("name");
            Optional<AlgorithmStatistics> found = statisticsService.getAllAlgorithmStatistics().stream()
                    .filter(a -> a.getAlgorithmName().equalsIgnoreCase(name))
                    .findFirst();
            if (found.isPresent()) {
                ctx.json(found.get());
            } else {
                ctx.status(404).json(Map.of("error", "Algorithm not found: " + name));
            }
        });
    }
}
