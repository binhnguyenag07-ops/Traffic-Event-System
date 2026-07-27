package team.traffic.domain.vehicle;

import java.util.Map;
import java.util.List;
import team.traffic.domain.vehicle.dynamics.DriverProfile;
import team.traffic.domain.vehicle.navigation.DriverDecisionProfile;

public class VehicleFactory {
    private static final int DEFAULT_BUS_CAPACITY = 40;
    private static final int DEFAULT_EMERGENCY_PRIORITY = 1;
    private Map<VehicleType, DriverDecisionProfile> decisionProfiles = Map.of();
    private double minimumProgressMetres = 1.0;
    private List<Double> retryBackoffSeconds = List.of(5.0, 15.0, 30.0, 60.0);
    private long scenarioSeed;

    public void setDecisionProfiles(
            Map<VehicleType, DriverDecisionProfile> decisionProfiles) {
        this.decisionProfiles = decisionProfiles == null
                ? Map.of()
                : Map.copyOf(decisionProfiles);
    }

    public void setStallTracking(
            double minimumProgressMetres,
            List<Double> retryBackoffSeconds) {
        this.minimumProgressMetres = minimumProgressMetres;
        this.retryBackoffSeconds = List.copyOf(retryBackoffSeconds);
    }

    public void setScenarioSeed(long scenarioSeed) {
        this.scenarioSeed = scenarioSeed;
    }

    public Vehicle createVehicle(VehicleType type, String currentIntersectionId, String destinationIntersectionId) {
        return switch (type) {
            case CAR -> createCar(currentIntersectionId, destinationIntersectionId);
            case MOTORCYCLE -> createMotorcycle(currentIntersectionId, destinationIntersectionId);
            case BUS -> createBus(currentIntersectionId, destinationIntersectionId);
            case EMERGENCY -> createEmergencyVehicle(currentIntersectionId, destinationIntersectionId);
        };
    }

    public Vehicle createVehicle(
            VehicleType type,
            String id,
            String currentIntersectionId,
            String destinationIntersectionId) {
        return switch (type) {
            case CAR -> createCar(id, currentIntersectionId, destinationIntersectionId);
            case MOTORCYCLE -> createMotorcycle(id, currentIntersectionId, destinationIntersectionId);
            case BUS -> createBus(id, currentIntersectionId, destinationIntersectionId);
            case EMERGENCY -> createEmergencyVehicle(id, currentIntersectionId, destinationIntersectionId);
        };
    }

    public Car createCar(String currentIntersectionId, String destinationIntersectionId) {
        return createCar("car-" + currentIntersectionId + "-" + destinationIntersectionId,
                currentIntersectionId,
                destinationIntersectionId);
    }

    public Bus createBus(String currentIntersectionId, String destinationIntersectionId) {
        return createBus("bus-" + currentIntersectionId + "-" + destinationIntersectionId,
                currentIntersectionId,
                destinationIntersectionId);
    }

    public Motorcycle createMotorcycle(String currentIntersectionId, String destinationIntersectionId) {
        return createMotorcycle("motorcycle-" + currentIntersectionId + "-" + destinationIntersectionId,
                currentIntersectionId,
                destinationIntersectionId);
    }

    public EmergencyVehicle createEmergencyVehicle(String currentIntersectionId, String destinationIntersectionId) {
        return createEmergencyVehicle("emergency-" + currentIntersectionId + "-" + destinationIntersectionId,
                currentIntersectionId,
                destinationIntersectionId);
    }

    public Car createCar(String id, String currentIntersectionId, String destinationIntersectionId) {
        return configureVehicle(new Car(id, currentIntersectionId, destinationIntersectionId));
    }

    public Motorcycle createMotorcycle(String id, String currentIntersectionId, String destinationIntersectionId) {
        return configureVehicle(new Motorcycle(id, currentIntersectionId, destinationIntersectionId));
    }

    public Bus createBus(String id, String currentIntersectionId, String destinationIntersectionId) {
        return configureVehicle(new Bus(
                id, currentIntersectionId, destinationIntersectionId, DEFAULT_BUS_CAPACITY));
    }

    public EmergencyVehicle createEmergencyVehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId) {
        return configureVehicle(new EmergencyVehicle(
                id, currentIntersectionId, destinationIntersectionId, DEFAULT_EMERGENCY_PRIORITY));
    }

    /** Applies the same strategic profile to scripted, demand, and manual vehicles. */
    public <T extends Vehicle> T configureVehicle(T vehicle) {
        if (vehicle == null) {
            throw new IllegalArgumentException("Vehicle is required.");
        }
        vehicle.configureStallTracking(
                minimumProgressMetres, retryBackoffSeconds);
        DriverDecisionProfile decision = decisionProfiles.getOrDefault(
                vehicle.getType(),
                DriverDecisionProfile.forType(vehicle.getType()));
        DriverProfile motion = vehicle.getDriverProfile();
        vehicle.setDriverProfile(new DriverProfile(
                motion.minimumGapMetres(),
                motion.reactionTimeSeconds(),
                motion.imperfection(),
                motion.gapAcceptanceSeconds(),
                motion.speedFactor(),
                motion.lateralAccelerationMps2(),
                motion.politeness(),
                motion.maxLateralSpeedMps(),
                decision.variedFor(vehicle.getId(), scenarioSeed)));
        return vehicle;
    }
}
