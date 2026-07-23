package team.traffic.domain.vehicle;

import team.traffic.domain.vehicle.dynamics.FilteringLaneChangeModel;

/** Compact two-wheel vehicle with its own safe, low-threshold lane strategy. */
public class Motorcycle extends Vehicle {
    private static final double DEFAULT_CRUISING_SPEED_KPH = 50.0;

    public Motorcycle(String id, String currentIntersectionId, String destinationIntersectionId) {
        this(id, currentIntersectionId, destinationIntersectionId,
                VehicleProfile.motorcycle(), DEFAULT_CRUISING_SPEED_KPH);
    }

    public Motorcycle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId,
            VehicleProfile profile,
            double cruisingSpeedKph) {
        super(id, currentIntersectionId, destinationIntersectionId, profile, cruisingSpeedKph);
        setLaneChangeModel(new FilteringLaneChangeModel());
    }

    @Override
    public VehicleType getType() {
        return VehicleType.MOTORCYCLE;
    }
}
