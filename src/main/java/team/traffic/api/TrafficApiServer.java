package team.traffic.api;

import io.javalin.Javalin;

public class TrafficApiServer {
    private final Javalin app;
    private final int port;

    public TrafficApiServer(ApiRouteRegistry routeRegistry, int port) {
        if (routeRegistry == null) {
            throw new IllegalArgumentException("Route registry is required");
        }
        this.port = port;
        this.app = Javalin.create(config -> {
            config.bundledPlugins.enableCors(cors -> cors.addRule(rule -> rule.anyHost()));
        });
        routeRegistry.register(app);
    }

    public void start() {
        app.start(port);
    }

    public void stop() {
        app.stop();
    }
}
