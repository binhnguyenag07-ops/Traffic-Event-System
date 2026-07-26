package team.traffic.domain.demand;

import team.traffic.domain.vehicle.VehicleType;

public record VehicleMixEntry(VehicleType type, double share) {
    public VehicleMixEntry {
        if (type == null) throw new IllegalArgumentException("Vehicle mix type is required");
        if (!Double.isFinite(share) || share <= 0 || share > 1) {
            throw new IllegalArgumentException("Vehicle mix share must be in (0, 1]");
        }
    }
}
