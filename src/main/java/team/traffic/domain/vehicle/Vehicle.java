package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import team.traffic.domain.routing.Route;
import team.traffic.service.RoutingService;

public abstract class Vehicle {
    private final String id;
    private String currentIntersectionId;
    private final String destinationIntersectionId;
    private Route currentRoute;
    private double movementSpeed;
    private VehicleStatus status;
    private final List<String> travelHistory;

    protected Vehicle(String id, String currentIntersectionId, String destinationIntersectionId, double movementSpeed) {
        this.id = id;
        this.currentIntersectionId = currentIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.movementSpeed = movementSpeed;
        this.status = VehicleStatus.WAITING;
        this.travelHistory = new ArrayList<>();
        this.travelHistory.add(currentIntersectionId);
    }

    public abstract VehicleType getType();

    public void requestRoute(RoutingService routingService) {
        this.currentRoute = null;
        this.status = VehicleStatus.WAITING;
    }

    public void moveOneStep() {
        if (hasReachedDestination()) {
            this.status = VehicleStatus.ARRIVED;
            return;
        }

        this.status = currentRoute == null ? VehicleStatus.BLOCKED : VehicleStatus.MOVING;
    }

    public void recomputeRoute(RoutingService routingService) {
        requestRoute(routingService);
    }

    public void moveTo(String nextIntersectionId) {
        if (nextIntersectionId == null || nextIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Next intersection id must not be blank.");
        }

        this.currentIntersectionId = nextIntersectionId;
        this.travelHistory.add(nextIntersectionId);
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.MOVING;
    }

    public boolean hasReachedDestination() {
        return destinationIntersectionId.equals(currentIntersectionId);
    }

    public String getId() {
        return id;
    }

    public String getCurrentIntersectionId() {
        return currentIntersectionId;
    }

    public String getDestinationIntersectionId() {
        return destinationIntersectionId;
    }

    public Route getCurrentRoute() {
        return currentRoute;
    }

    public void setCurrentRoute(Route currentRoute) {
        this.currentRoute = currentRoute;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(double movementSpeed) {
        if (movementSpeed <= 0) {
            throw new IllegalArgumentException("Movement speed must be positive.");
        }
        this.movementSpeed = movementSpeed;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public List<String> getTravelHistory() {
        return Collections.unmodifiableList(travelHistory);
    }
}
