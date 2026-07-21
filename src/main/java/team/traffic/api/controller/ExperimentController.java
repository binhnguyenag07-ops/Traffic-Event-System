package team.traffic.api.controller;

import io.javalin.Javalin;
import java.util.Map;
import team.traffic.experiment.ExperimentService;

public class ExperimentController {
    private final ExperimentService service;

    public ExperimentController(ExperimentService service) {
        this.service = service;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/experiments/latest", ctx -> run(ctx, false));
        app.post("/api/experiments/run", ctx -> run(ctx, true));
    }

    private void run(io.javalin.http.Context ctx, boolean refresh) {
        try {
            ctx.json(refresh ? service.run() : service.getLatest());
        } catch (RuntimeException exception) {
            ctx.status(500).json(Map.of("error", exception.getMessage()));
        }
    }
}
