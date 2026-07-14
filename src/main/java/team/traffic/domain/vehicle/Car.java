package team.traffic.domain.vehicle;

public class Car extends Vehicle {
    private static final double DEFAULT_LENGTH = 4.5;
    private static final double DEFAULT_WIDTH = 1.8;
    private static final double DEFAULT_MAX_SPEED = 120.0;
    private static final double DEFAULT_CURRENT_SPEED = 50.0;
    private static final double DEFAULT_ACCELERATION = 2.5;
    private static final double DEFAULT_DECELERATION = 6.5;
    private static final double DEFAULT_WEIGHT = 1_500.0;

    public Car(String id, String currentIntersectionId, String destinationIntersectionId) {
        this(
                id,
                currentIntersectionId,
                destinationIntersectionId,
                DEFAULT_LENGTH,
                DEFAULT_WIDTH,
                DEFAULT_MAX_SPEED,
                DEFAULT_CURRENT_SPEED,
                DEFAULT_ACCELERATION,
                DEFAULT_DECELERATION,
                DEFAULT_WEIGHT);
    }

    public Car(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
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
                0);
    }

    @Override
    public VehicleType getType() {
        return VehicleType.CAR;
    }
}
