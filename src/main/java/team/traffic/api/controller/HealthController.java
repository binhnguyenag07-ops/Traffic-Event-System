package team.traffic.api.controller;

import io.javalin.Javalin;

import java.util.Map;

public class HealthController {
    public void registerRoutes(Javalin app) {
        app.get("/api/health", ctx -> ctx.json(Map.of(
                "status", "UP",
                "service", "Traffic Event System")));
    }
}
