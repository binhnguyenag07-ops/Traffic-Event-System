package team.traffic.simulation;

import team.traffic.service.RoadNetworkService;
import team.traffic.service.RoutingService;
import team.traffic.service.StatisticsService;
import team.traffic.service.TrafficEventService;
import team.traffic.service.VehicleService;

public class TrafficSimulator {
    private RoadNetworkService roadNetworkService;
    private VehicleService vehicleService;
    private RoutingService routingService;
    private TrafficEventService trafficEventService;
    private StatisticsService statisticsService;
    private SimulationClock clock = new SimulationClock(1000L);
    private SimulationStatus status = SimulationStatus.CREATED;

    public void start() {
        status = SimulationStatus.RUNNING;
    }

    public void pause() {
        status = SimulationStatus.PAUSED;
    }

    public void resume() {
        status = SimulationStatus.RUNNING;
    }

    public void reset() {
        clock.reset();
        status = SimulationStatus.CREATED;
    }

    public void advanceOneTick() {
        // TODO: process events, recompute affected routes, move vehicles, and collect statistics.
        clock.advance();
    }

    public SimulationSnapshot createSnapshot() {
        SimulationSnapshot snapshot = new SimulationSnapshot();
        snapshot.setTick(clock.getCurrentTick());
        snapshot.setStatus(status);
        if (roadNetworkService != null) {
            snapshot.setRoadNetwork(roadNetworkService.getRoadNetwork());
        }
        if (vehicleService != null) {
            snapshot.setVehicles(vehicleService.getActiveVehicles());
        }
        if (trafficEventService != null) {
            snapshot.setActiveEvents(trafficEventService.getActiveEvents());
        }
        if (statisticsService != null) {
            snapshot.setStatistics(statisticsService.calculateTrafficStatistics());
        }
        return snapshot;
    }

    public SimulationStatus getStatus() {
        return status;
    }

    public RoadNetworkService getRoadNetworkService() {
        return roadNetworkService;
    }

    public void setRoadNetworkService(RoadNetworkService roadNetworkService) {
        this.roadNetworkService = roadNetworkService;
    }

    public VehicleService getVehicleService() {
        return vehicleService;
    }

    public void setVehicleService(VehicleService vehicleService) {
        this.vehicleService = vehicleService;
    }

    public RoutingService getRoutingService() {
        return routingService;
    }

    public void setRoutingService(RoutingService routingService) {
        this.routingService = routingService;
    }

    public TrafficEventService getTrafficEventService() {
        return trafficEventService;
    }

    public void setTrafficEventService(TrafficEventService trafficEventService) {
        this.trafficEventService = trafficEventService;
    }

    public StatisticsService getStatisticsService() {
        return statisticsService;
    }

    public void setStatisticsService(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }
}
