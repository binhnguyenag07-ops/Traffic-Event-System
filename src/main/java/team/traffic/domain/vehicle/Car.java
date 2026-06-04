package team.traffic.domain.vehicle;

public class Car extends Vehicle {
    public Car(String id, String currentIntersectionId, String destinationIntersectionId) {
        super(id, currentIntersectionId, destinationIntersectionId);
        setMovementSpeed(50.0);
        setStatus(VehicleStatus.WAITING_FOR_ROUTE);
    }

    @Override
    public String getType() {
        return VehicleType.CAR;
    }
}
