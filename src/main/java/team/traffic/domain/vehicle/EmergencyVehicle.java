package team.traffic.domain.vehicle;

public class EmergencyVehicle extends Vehicle {
    private static final double DEFAULT_SPEED = 70.0;
    private final int priorityLevel;

    public EmergencyVehicle(String id, String currentIntersectionId, String destinationIntersectionId, int priorityLevel) {
        super(id, currentIntersectionId, destinationIntersectionId, DEFAULT_SPEED);
        if (priorityLevel <= 0) {
            throw new IllegalArgumentException("Priority level must be positive.");
        }
        this.priorityLevel = priorityLevel;
    }

    @Override
    public VehicleType getType() {
        return VehicleType.EMERGENCY;
    }

    public int getPriorityLevel() {
        return priorityLevel;
    }
}
