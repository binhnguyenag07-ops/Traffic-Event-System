package team.traffic.domain.vehicle.dynamics;

/**
 * What the microscopic model needs to know about something moving on a road.
 *
 * <p>The model does not need a whole vehicle. It needs to know how long the
 * thing is, where on its road it sits, how fast it is going, how its driver
 * behaves, and whether it outranks anyone else at a junction. Depending on that
 * much and no more keeps this package free of any knowledge of routing, trip
 * statistics, or traffic-light delays.</p>
 *
 * <p>It also breaks a dependency cycle. This package previously imported
 * {@code Vehicle} while {@code Vehicle} imported this package, so neither could
 * be reasoned about or tested without the other. The dependency now runs one
 * way: a vehicle knows the model it obeys, and the model knows only this.</p>
 */
public interface TrafficParticipant {

    /** Stable identity, used to break ties so results stay reproducible. */
    String getId();

    /** Bumper-to-bumper length in metres. */
    double getLength();

    /** How far along its current road it is, from 0 at the entry to 1 at the exit. */
    double getRoadProgress();

    /** The road it currently occupies, or {@code null} while crossing a junction. */
    String getOccupiedRoadId();

    /** Speed it is actually travelling at, in metres per second. */
    double getInstantaneousSpeedMps();

    /** Dispatch priority at a junction. Ordinary traffic uses {@code 0}. */
    int getPriorityLevel();

    /** Behaviour parameters of whoever is driving. */
    DriverProfile getDriverProfile();
}
