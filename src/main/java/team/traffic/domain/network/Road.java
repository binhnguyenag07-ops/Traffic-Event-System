package main.java.team.traffic.domain.network;



public class Road {
    private String id;
    private String startIntersectionId;
    private String endIntersectionId;
    private double distance;
    private double speedLimit;
    private int capacity;
    private int currentVehicleCount;
    private RoadCondition roadCondition;

    public Road(String id, String startIntersectionId, String endIntersectionId,
            double distance, double speedLimit, int capacity,
            int currentVehicleCount, RoadCondition roadCondition) {
        this.id = id;
        this.startIntersectionId = startIntersectionId;
        this.endIntersectionId = endIntersectionId;
        this.distance = distance;
        this.speedLimit = speedLimit;
        this.capacity = capacity;
        this.currentVehicleCount = currentVehicleCount;
        this.roadCondition = roadCondition;
    }
    
    public double calculateTravelCost() {
        double TravelCost = 0;
        return TravelCost;
    }

    public boolean isAvailable() {
        return true;
    }
}