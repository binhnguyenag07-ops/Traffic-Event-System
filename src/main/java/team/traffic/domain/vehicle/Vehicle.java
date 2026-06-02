package team.traffic.domain.vehicle;

import team.traffic.domain.network.LocationType;
import team.traffic.domain.routing.Route;

public abstract class Vehicle {
    protected String id;
    protected String currentIntersectionId;
    protected String destinationIntersectionId;
    protected Route currentRoute;
    protected double movementSpeed;
    protected String status;
    
    // New fields for behavior and accident probability
    protected double accidentProbabilityModifier;
    protected LocationType lastVisitedLocation;

    public Vehicle(String id, String currentIntersectionId, String destinationIntersectionId) {
        this.id = id;
        this.currentIntersectionId = currentIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.accidentProbabilityModifier = 1.0;
        this.lastVisitedLocation = LocationType.NORMAL;
    }

    public abstract String getType();

    public String getId() { return id; }
    public String getCurrentIntersectionId() { return currentIntersectionId; }
    public void setCurrentIntersectionId(String currentIntersectionId) { this.currentIntersectionId = currentIntersectionId; }
    
    public String getDestinationIntersectionId() { return destinationIntersectionId; }
    
    public Route getCurrentRoute() { return currentRoute; }
    public void setCurrentRoute(Route currentRoute) { this.currentRoute = currentRoute; }

    public double getMovementSpeed() { return movementSpeed; }
    public void setMovementSpeed(double movementSpeed) { this.movementSpeed = movementSpeed; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getAccidentProbabilityModifier() { return accidentProbabilityModifier; }
    public void setAccidentProbabilityModifier(double accidentProbabilityModifier) { this.accidentProbabilityModifier = accidentProbabilityModifier; }

    public LocationType getLastVisitedLocation() { return lastVisitedLocation; }
    
    public void setLastVisitedLocation(LocationType location) { 
        this.lastVisitedLocation = location;
        if (location != null) {
            this.accidentProbabilityModifier *= location.getIntoxicationFactor();
        }
    }

    public void moveOneStep() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    public void recomputeRoute() {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
