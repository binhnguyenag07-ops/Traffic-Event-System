package team.traffic.domain.vehicle;

public class Bus extends Vehicle {
    private int passengerCapacity;

    public Bus(String id, String currentIntersectionId, String destinationIntersectionId, int passengerCapacity) {
        super(id, currentIntersectionId, destinationIntersectionId);
        this.passengerCapacity = passengerCapacity;
        setMovementSpeed(35.0);
        setStatus(VehicleStatus.WAITING_FOR_ROUTE);
    }

    @Override
    public String getType() {
        return VehicleType.BUS;
    }

    public int getPassengerCapacity() {
        return passengerCapacity;
    }

    public void setPassengerCapacity(int passengerCapacity) {
        this.passengerCapacity = passengerCapacity;
    }
}
