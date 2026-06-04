package team.traffic.api;

import team.traffic.config.ApplicationConfig;

public class TrafficApiServer {
    private final ApplicationConfig config;
    private final ApiRouteRegistry routeRegistry;

    public TrafficApiServer(ApplicationConfig config) {
        this.config = config;
        this.routeRegistry = new ApiRouteRegistry();
    }

    public void start() {
        // TODO: bind com.sun.net.httpserver.HttpServer routes from ApiRouteRegistry.
        throw new UnsupportedOperationException("TODO: implement HTTP server startup.");
    }

    public void stop() {
        // TODO: stop the HTTP server when it is implemented.
        throw new UnsupportedOperationException("TODO: implement HTTP server shutdown.");
    }

    public ApplicationConfig getConfig() {
        return config;
    }

    public ApiRouteRegistry getRouteRegistry() {
        return routeRegistry;
    }
}
