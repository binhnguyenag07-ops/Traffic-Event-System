package team.traffic.api.controller;

import team.traffic.api.dto.ApiResponse;

public class HealthController {
    public ApiResponse<String> health() {
        return ApiResponse.success("Traffic Event System backend is reachable.");
    }
}
