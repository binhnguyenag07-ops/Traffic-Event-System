package team.traffic.domain.vehicle;

import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteStep;

/**
 * The route a vehicle is following and how far through it the vehicle is.
 *
 * <p>A route on its own says nothing about progress, and an index on its own
 * has nothing to index into. Keeping them apart meant every question about the
 * journey had to re-establish that both existed and that the index was in
 * range, so the same guard —
 * {@code route == null || index + n >= route.getStepCount()} — was written out
 * four times, once per question asked.</p>
 *
 * <p>{@link #stepAt(int)} asks all four of those questions with one bounds
 * check, returning {@code null} for a step that is not there rather than
 * throwing. Reassigning a route resets the progress in the same call, so the
 * two cannot disagree.</p>
 */
public final class RoutePlan {
    private Route route;
    private int nextStepIndex;
    private RoutePreference preference = RoutePreference.CONGESTION_AWARE;
    private String lastAlgorithmName;

    /** Takes a new route and starts again from its first step. */
    public void assign(Route route, String algorithmName) {
        this.route = route;
        this.nextStepIndex = 0;
        this.lastAlgorithmName = algorithmName;
    }

    /** Discards the route, leaving the vehicle with nowhere to go. */
    public void clear() {
        this.route = null;
        this.nextStepIndex = 0;
    }

    /**
     * Step {@code offset} places from the one the vehicle is about to take, or
     * {@code null} when there is no such step.
     *
     * <p>Offset zero is the step being taken now, {@code 1} the one after it,
     * and {@code -1} the road just left.</p>
     */
    public RouteStep stepAt(int offset) {
        if (route == null) {
            return null;
        }
        int index = nextStepIndex + offset;
        if (index < 0 || index >= route.getStepCount()) {
            return null;
        }
        return route.getSteps().get(index);
    }

    /** The step the vehicle is about to take, or {@code null} at the end. */
    public RouteStep currentStep() {
        return stepAt(0);
    }

    /** Marks the current step as taken. */
    public void advance() {
        nextStepIndex++;
    }

    /** Whether every step has been taken. */
    public boolean isExhausted() {
        return route == null || nextStepIndex >= route.getStepCount();
    }

    public Route getRoute() {
        return route;
    }

    public int getNextStepIndex() {
        return nextStepIndex;
    }

    public RoutePreference getPreference() {
        return preference;
    }

    public void setPreference(RoutePreference preference) {
        if (preference == null) {
            throw new IllegalArgumentException("Route preference must not be null.");
        }
        this.preference = preference;
    }

    /** Name of the algorithm that produced the current route, for statistics. */
    public String getLastAlgorithmName() {
        return lastAlgorithmName;
    }
}
