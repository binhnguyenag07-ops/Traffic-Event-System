package team.traffic.domain.vehicle;

public class EmergencyVehicle extends Vehicle {
    private int priorityLevel;

    public EmergencyVehicle(String id, String currentIntersectionId, String destinationIntersectionId, int priorityLevel) {
        super(id, currentIntersectionId, destinationIntersectionId);
        this.priorityLevel = priorityLevel;
        setMovementSpeed(70.0);
        setStatus(VehicleStatus.WAITING_FOR_ROUTE);
    }

    @Override
    public String getType() {
        return VehicleType.EMERGENCY;
    }

    public int getPriorityLevel() {
        return priorityLevel;
    }

    public void setPriorityLevel(int priorityLevel) {
        this.priorityLevel = priorityLevel;
    }
}
