package team.traffic.domain.vehicle;

public class Car extends Vehicle {
    private static final double DEFAULT_SPEED = 50.0;

    public Car(String id, String currentIntersectionId, String destinationIntersectionId) {
        super(id, currentIntersectionId, destinationIntersectionId, DEFAULT_SPEED);
    }

    @Override
    public VehicleType getType() {
        return VehicleType.CAR;
    }
}
