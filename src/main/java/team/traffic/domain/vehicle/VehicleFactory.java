package team.traffic.domain.vehicle;

public class VehicleFactory {
    private static final int DEFAULT_BUS_CAPACITY = 40;
    private static final int DEFAULT_EMERGENCY_PRIORITY = 1;

    public Vehicle createVehicle(VehicleType type, String currentIntersectionId, String destinationIntersectionId) {
        return switch (type) {
            case CAR -> createCar(currentIntersectionId, destinationIntersectionId);
            case BUS -> createBus(currentIntersectionId, destinationIntersectionId);
            case EMERGENCY -> createEmergencyVehicle(currentIntersectionId, destinationIntersectionId);
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

    public EmergencyVehicle createEmergencyVehicle(String currentIntersectionId, String destinationIntersectionId) {
        return createEmergencyVehicle("emergency-" + currentIntersectionId + "-" + destinationIntersectionId,
                currentIntersectionId,
                destinationIntersectionId);
    }

    public Car createCar(String id, String currentIntersectionId, String destinationIntersectionId) {
        return new Car(id, currentIntersectionId, destinationIntersectionId);
    }

    public Bus createBus(String id, String currentIntersectionId, String destinationIntersectionId) {
        return new Bus(id, currentIntersectionId, destinationIntersectionId, DEFAULT_BUS_CAPACITY);
    }

    public EmergencyVehicle createEmergencyVehicle(
            String id,
            String currentIntersectionId,
            String destinationIntersectionId) {
        return new EmergencyVehicle(id, currentIntersectionId, destinationIntersectionId, DEFAULT_EMERGENCY_PRIORITY);
    }
}
