package team.traffic.api.dto;

import java.util.List;

public class RouteResultDto {
    public String algorithm;
    public double totalDistance;
    public double estimatedTravelTime;
    public double totalCost;
    public long computationTimeMillis;
    public int visitedNodes;
    public List<RouteStepDto> steps;

    public static class RouteStepDto {
        public String from;
        public String to;
        public String roadId;
        public double cost;

        public RouteStepDto(String from, String to, String roadId, double cost) {
            this.from = from;
            this.to = to;
            this.roadId = roadId;
            this.cost = cost;
        }
    }
}
