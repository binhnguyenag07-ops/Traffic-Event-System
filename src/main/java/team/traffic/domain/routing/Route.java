package team.traffic.domain.routing;

import java.util.ArrayList;
import java.util.List;

public class Route {
    private final List<RouteStep> steps = new ArrayList<>();
    private double totalDistance;
    private double estimatedTravelTime;
    private double totalCost;

    public List<RouteStep> getSteps() {
        return steps;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public double getEstimatedTravelTime() {
        return estimatedTravelTime;
    }

    public void setEstimatedTravelTime(double estimatedTravelTime) {
        this.estimatedTravelTime = estimatedTravelTime;
    }

    public double getTotalCost() {
        return totalCost;
    }

    public void setTotalCost(double totalCost) {
        this.totalCost = totalCost;
    }
}
