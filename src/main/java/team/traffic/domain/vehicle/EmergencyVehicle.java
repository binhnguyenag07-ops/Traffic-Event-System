package team.traffic.domain.vehicle;

public class EmergencyVehicle extends Vehicle {
    private static final double DEFAULT_CRUISING_SPEED_KPH = 70.0;

    public EmergencyVehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            int priorityLevel) {
        this(id, currentIntersectionId, destinationIntersectionId,
                VehicleProfile.emergency(priorityLevel), DEFAULT_CRUISING_SPEED_KPH);
    }

    public EmergencyVehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            VehicleProfile profile,
            double cruisingSpeedKph) {
        super(id, currentIntersectionId, destinationIntersectionId, profile, cruisingSpeedKph);
        if (profile.priorityLevel() <= 0) {
            throw new IllegalArgumentException("Priority level must be positive.");
        }
    }

    @Override
    public VehicleType getType() {
        return VehicleType.EMERGENCY;
    }
}
