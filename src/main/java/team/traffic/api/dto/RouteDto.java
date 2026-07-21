package team.traffic.api.dto;

import java.util.List;
import team.traffic.domain.routing.Route;

public class RouteDto {
    private List<RouteResultDto.RouteStepDto> steps;
    private double totalDistance;
    private double estimatedTravelTime;
    private double totalCost;

    public RouteDto() {}

    public RouteDto(Route route) {
        this.steps = route.getSteps().stream()
                .map(step -> new RouteResultDto.RouteStepDto(
                        step.getFromIntersectionId(), step.getToIntersectionId(), step.getRoadId(), step.getCost()))
                .toList();
        this.totalDistance = route.getTotalDistance();
        this.estimatedTravelTime = route.getEstimatedTravelTime();
        this.totalCost = route.getTotalCost();
    }

    public List<RouteResultDto.RouteStepDto> getSteps() { return steps; }
    public void setSteps(List<RouteResultDto.RouteStepDto> steps) { this.steps = steps; }
    public double getTotalDistance() { return totalDistance; }
    public void setTotalDistance(double totalDistance) { this.totalDistance = totalDistance; }
    public double getEstimatedTravelTime() { return estimatedTravelTime; }
    public void setEstimatedTravelTime(double estimatedTravelTime) { this.estimatedTravelTime = estimatedTravelTime; }
    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }
}
