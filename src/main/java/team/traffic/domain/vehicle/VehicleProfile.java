package team.traffic.domain.vehicle;

/**
 * The physical make-up of a vehicle: how big it is, how hard it can push and
 * pull, how heavy it is, and how far it outranks other traffic.
 *
 * <p>Every one of these is fixed for the life of the vehicle, which is why they
 * belong together in one immutable value rather than as seven separate fields
 * that each need their own validation and their own accessor. The driver's
 * behaviour, which can differ between two people in identical vehicles, is kept
 * apart in {@link team.traffic.domain.vehicle.dynamics.DriverProfile}.</p>
 *
 * @param lengthMetres      bumper to bumper
 * @param widthMetres       mirror to mirror
 * @param maxSpeedKph       fastest the vehicle can travel
 * @param accelerationMps2  acceleration it can produce
 * @param decelerationMps2  comfortable braking rate
 * @param weightKilograms   mass
 * @param priorityLevel     dispatch priority at a junction; ordinary traffic
 *                          uses {@code 0} and emergency vehicles more
 */
public record VehicleProfile(
        double lengthMetres,
        double widthMetres,
        double maxSpeedKph,
        double accelerationMps2,
        double decelerationMps2,
        double weightKilograms,
        int priorityLevel) {

    public VehicleProfile {
        requirePositive("Length", lengthMetres);
        requirePositive("Width", widthMetres);
        requirePositive("Maximum speed", maxSpeedKph);
        requirePositive("Acceleration", accelerationMps2);
        requirePositive("Deceleration", decelerationMps2);
        requirePositive("Vehicle weight", weightKilograms);
        if (priorityLevel < 0) {
            throw new IllegalArgumentException("Priority level must not be negative.");
        }
    }

    public static VehicleProfile car() {
        return new VehicleProfile(4.5, 1.8, 120.0, 2.5, 6.5, 1_500.0, 0);
    }

    public static VehicleProfile bus() {
        return new VehicleProfile(12.0, 2.5, 90.0, 1.0, 5.0, 12_000.0, 0);
    }

    public static VehicleProfile emergency(int priorityLevel) {
        if (priorityLevel <= 0) {
            throw new IllegalArgumentException("Priority level must be positive.");
        }
        return new VehicleProfile(5.0, 2.0, 140.0, 3.0, 7.5, 2_500.0, priorityLevel);
    }

    /** Rejects a cruising speed the vehicle could not actually hold. */
    public void requireReachable(double speedKph) {
        if (!Double.isFinite(speedKph) || speedKph < 0 || speedKph > maxSpeedKph) {
            throw new IllegalArgumentException("Current speed must be between zero and the maximum speed.");
        }
    }

    private static void requirePositive(String name, double value) {
        if (!Double.isFinite(value) || value <= 0) {
            throw new IllegalArgumentException(name + " must be positive.");
        }
    }
}
