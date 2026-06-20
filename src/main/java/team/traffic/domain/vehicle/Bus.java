package team.traffic.domain.vehicle;

public class Bus extends Vehicle {
    private static final double DEFAULT_SPEED = 35.0;
    private final int passengerCapacity;

    public Bus(String id, String currentIntersectionId, String destinationIntersectionId, int passengerCapacity) {
        super(id, currentIntersectionId, destinationIntersectionId, DEFAULT_SPEED);
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
