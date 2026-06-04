package team.traffic.api.controller;

import team.traffic.api.dto.ApiResponse;
import team.traffic.service.SimulationService;
import team.traffic.simulation.SimulationSnapshot;

public class SimulationController {
    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    public ApiResponse<Void> start() {
        simulationService.start();
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> pause() {
        simulationService.pause();
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> resume() {
        simulationService.resume();
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> reset() {
        simulationService.reset();
        return ApiResponse.success(null);
    }

    public ApiResponse<Void> advanceOneTick() {
        simulationService.advanceOneTick();
        return ApiResponse.success(null);
    }

    public ApiResponse<SimulationSnapshot> getSnapshot() {
        return ApiResponse.success(simulationService.getSnapshot());
    }
}
