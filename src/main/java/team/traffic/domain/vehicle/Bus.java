package team.traffic.domain.vehicle;

public class Bus extends Vehicle {
    private static final double DEFAULT_LENGTH = 12.0;
    private static final double DEFAULT_WIDTH = 2.5;
    private static final double DEFAULT_MAX_SPEED = 90.0;
    private static final double DEFAULT_CURRENT_SPEED = 35.0;
    private static final double DEFAULT_ACCELERATION = 1.0;
    private static final double DEFAULT_DECELERATION = 5.0;
    private static final double DEFAULT_WEIGHT = 12_000.0;
    private final int passengerCapacity;

    public Bus(String id, String currentIntersectionId, String destinationIntersectionId, int passengerCapacity) {
        this(
                id,
                currentIntersectionId,
                destinationIntersectionId,
                passengerCapacity,
                DEFAULT_LENGTH,
                DEFAULT_WIDTH,
                DEFAULT_MAX_SPEED,
                DEFAULT_CURRENT_SPEED,
                DEFAULT_ACCELERATION,
                DEFAULT_DECELERATION,
                DEFAULT_WEIGHT);
    }

    public Bus(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            int passengerCapacity,
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
        if (passengerCapacity <= 0) {
            throw new IllegalArgumentException("Passenger capacity must be positive.");
        }
        this.passengerCapacity = passengerCapacity;
    }

    @Override
    public VehicleType getType() {
        return VehicleType.BUS;
    }

    public int getPassengerCapacity() {
        return passengerCapacity;
    }
}
