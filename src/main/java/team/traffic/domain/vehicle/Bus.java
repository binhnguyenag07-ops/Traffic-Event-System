package team.traffic.domain.vehicle;

public class Bus extends Vehicle {
    private static final double DEFAULT_CRUISING_SPEED_KPH = 35.0;
    private final int passengerCapacity;

    public Bus(String id, String currentIntersectionId, String destinationIntersectionId, int passengerCapacity) {
        this(id, currentIntersectionId, destinationIntersectionId, passengerCapacity,
                VehicleProfile.bus(), DEFAULT_CRUISING_SPEED_KPH);
    }

    public Bus(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            int passengerCapacity,
            VehicleProfile profile,
            double cruisingSpeedKph) {
        super(id, currentIntersectionId, destinationIntersectionId, profile, cruisingSpeedKph);
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
