package team.traffic.domain.vehicle;

public class EmergencyVehicle extends Vehicle {
    private static final double DEFAULT_LENGTH = 5.0;
    private static final double DEFAULT_WIDTH = 2.0;
    private static final double DEFAULT_MAX_SPEED = 140.0;
    private static final double DEFAULT_CURRENT_SPEED = 70.0;
    private static final double DEFAULT_ACCELERATION = 3.0;
    private static final double DEFAULT_DECELERATION = 7.5;
    private static final double DEFAULT_WEIGHT = 2_500.0;

    public EmergencyVehicle(String id, String currentIntersectionId, String destinationIntersectionId, int priorityLevel) {
        this(
                id,
                currentIntersectionId,
                destinationIntersectionId,
                priorityLevel,
                DEFAULT_LENGTH,
                DEFAULT_WIDTH,
                DEFAULT_MAX_SPEED,
                DEFAULT_CURRENT_SPEED,
                DEFAULT_ACCELERATION,
                DEFAULT_DECELERATION,
                DEFAULT_WEIGHT);
    }

    public EmergencyVehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            int priorityLevel,
            double length,
            double width,
            double maxSpeed,
            double currentSpeed,
            double acceleration,
            double deceleration,
            double vehicleWeight) {
        super(
                id,
                currentIntersectionId,
                destinationIntersectionId,
                length,
                width,
                maxSpeed,
                currentSpeed,
                acceleration,
                deceleration,
                vehicleWeight,
                priorityLevel);
        if (priorityLevel <= 0) {
            throw new IllegalArgumentException("Priority level must be positive.");
        }
    }

    @Override
    public VehicleType getType() {
        return VehicleType.EMERGENCY;
    }
}
