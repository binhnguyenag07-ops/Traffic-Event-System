package team.traffic.domain.vehicle.dynamics;

/**
 * Chooses the speed a vehicle holds during the next simulation step.
 *
 * <p>This is the extension point of the microscopic layer. Swapping the
 * implementation changes how traffic behaves without touching the vehicle,
 * the junction logic, or the simulator.</p>
 */
public interface CarFollowingModel {

    /**
     * Returns the speed to hold for the coming step, in metres per second.
     * Implementations must never return a negative value and must never return
     * a speed that would make the vehicle pass its leader or its stop point.
     */
    double nextSpeedMps(FollowContext context);

    /** Identifier used in reports and traces. */
    String getName();
}
