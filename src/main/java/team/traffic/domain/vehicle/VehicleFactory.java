package team.traffic.domain.vehicle;

public class VehicleFactory {
    public Vehicle createVehicle(String id, String type, String startIntersectionId, String destinationIntersectionId) {
        // TODO: validate input ids and centralize default vehicle settings.
        if (VehicleType.BUS.equals(type)) {
            return createBus(id, startIntersectionId, destinationIntersectionId, 40);
        }
        if (VehicleType.EMERGENCY.equals(type)) {
            return createEmergencyVehicle(id, startIntersectionId, destinationIntersectionId, 10);
        }
        return createCar(id, startIntersectionId, destinationIntersectionId);
    }

    public Vehicle createCar(String id, String startIntersectionId, String destinationIntersectionId) {
        return new Car(id, startIntersectionId, destinationIntersectionId);
    }

    public Vehicle createBus(String id, String startIntersectionId, String destinationIntersectionId, int passengerCapacity) {
        return new Bus(id, startIntersectionId, destinationIntersectionId, passengerCapacity);
    }

    public Vehicle createEmergencyVehicle(String id, String startIntersectionId, String destinationIntersectionId, int priorityLevel) {
        return new EmergencyVehicle(id, startIntersectionId, destinationIntersectionId, priorityLevel);
    }
}
