package team.traffic.domain.vehicle;

public class Car extends Vehicle {
    private static final double DEFAULT_CRUISING_SPEED_KPH = 50.0;

    public Car(String id, String currentIntersectionId, String destinationIntersectionId) {
        this(id, currentIntersectionId, destinationIntersectionId,
                VehicleProfile.car(), DEFAULT_CRUISING_SPEED_KPH);
    }

    public Car(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            VehicleProfile profile,
            double cruisingSpeedKph) {
        super(id, currentIntersectionId, destinationIntersectionId, profile, cruisingSpeedKph);
    }

    @Override
    public VehicleType getType() {
        return VehicleType.CAR;
    }
}
