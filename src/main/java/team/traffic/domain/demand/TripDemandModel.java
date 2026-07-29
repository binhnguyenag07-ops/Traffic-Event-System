package team.traffic.domain.demand;

import java.util.List;

/** Strategy that produces resolved plans due in a simulation-time interval. */
public interface TripDemandModel {
    List<TripPlan> tripsBeginningIn(double fromExclusiveSeconds, double toInclusiveSeconds);

    /**
     * Complete deterministic schedule, when the strategy resolves plans ahead
     * of time. Exposing it makes generated demand auditable without coupling
     * the API to one concrete demand implementation.
     */
    default List<TripPlan> allPlans() {
        return List.of();
    }

    default int scheduledTripCount() {
        return 0;
    }

    /** Plans rejected while a strategy resolved its schedule. */
    default List<String> generationFailedPlanIds() {
        return List.of();
    }
}
