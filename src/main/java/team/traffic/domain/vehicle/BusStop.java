package team.traffic.domain.vehicle;

/** A junction at which a bus dwells before continuing its route. */
public record BusStop(String junctionId, double dwellSeconds) {
    public BusStop {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Bus-stop junction id is required.");
        }
        if (!Double.isFinite(dwellSeconds) || dwellSeconds <= 0) {
            throw new IllegalArgumentException("Bus-stop dwell time must be positive.");
        }
    }

    public static BusStop forPassengerCapacity(String junctionId, int passengerCapacity) {
        if (passengerCapacity <= 0) throw new IllegalArgumentException("Passenger capacity must be positive.");
        return new BusStop(junctionId, 5.0 + passengerCapacity * 0.15);
    }
}
