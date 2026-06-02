package hyo.traffic.service.impl;

import hyo.traffic.domain.vehicle.Vehicle;
import hyo.traffic.domain.vehicle.VehicleFactory;
import hyo.traffic.domain.vehicle.VehicleType;
import hyo.traffic.service.VehicleService;

import java.util.List;

public class VehicleServiceImpl implements VehicleService {

    private final VehicleFactory vehicleFactory;

    public VehicleServiceImpl(VehicleFactory vehicleFactory) {
        this.vehicleFactory = vehicleFactory;
    }

    @Override
    public Vehicle createVehicle(String id, VehicleType type, String startIntersectionId, String destinationIntersectionId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public Vehicle findVehicleById(String vehicleId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public List<Vehicle> getActiveVehicles() {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void removeVehicle(String vehicleId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void moveVehicleOneStep(String vehicleId) {
        throw new UnsupportedOperationException("TODO: implement");
    }

    @Override
    public void recomputeRouteForVehicle(String vehicleId) {
        throw new UnsupportedOperationException("TODO: implement");
    }
}
