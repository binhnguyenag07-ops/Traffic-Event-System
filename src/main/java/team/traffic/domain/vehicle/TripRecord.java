package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import team.traffic.domain.vehicle.dynamics.WaitCause;

/**
 * What a vehicle accumulated over its journey: where it went, how far, how
 * long, how much of that was spent waiting, and how often it had to think
 * again about its route.
 *
 * <p>These are the numbers the statistics service reports. They are pure
 * accumulation and have nothing to do with how a vehicle decides to move, so
 * they live apart from the vehicle's own state.</p>
 */
public final class TripRecord {

    private final List<String> visitedIntersections = new ArrayList<>();
    private double distanceTravelledKm;
    private double travelTimeSeconds;
    private long waitingTicks;
    private double waitingSeconds;
    private final Map<WaitCause, Double> waitingSecondsByCause = new EnumMap<>(WaitCause.class);
    private int routeRecalculationCount;
    private int legalUTurnsCompleted;

    public TripRecord(String startIntersectionId) {
        if (startIntersectionId == null || startIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Start intersection id must not be blank.");
        }
        visitedIntersections.add(startIntersectionId);
    }

    public void recordArrivalAt(String intersectionId) {
        if (intersectionId == null || intersectionId.isBlank()) {
            throw new IllegalArgumentException("Intersection id must not be blank.");
        }
        visitedIntersections.add(intersectionId);
    }

    public void addDistanceKm(double distanceKm) {
        if (!Double.isFinite(distanceKm) || distanceKm < 0) {
            throw new IllegalArgumentException("Distance must not be negative.");
        }
        distanceTravelledKm += distanceKm;
    }

    public void addTravelTime(double simulationSeconds) {
        if (!Double.isFinite(simulationSeconds) || simulationSeconds < 0) {
            throw new IllegalArgumentException("Simulation seconds must not be negative.");
        }
        travelTimeSeconds += simulationSeconds;
    }

    public void recordWaitingTick() {
        waitingTicks++;
    }

    public void recordWaiting(double seconds, WaitCause cause) {
        if (!Double.isFinite(seconds) || seconds < 0) {
            throw new IllegalArgumentException("Waiting duration must be finite and non-negative.");
        }
        if (cause == null || cause == WaitCause.NONE || seconds == 0) {
            return;
        }
        waitingSeconds += seconds;
        waitingSecondsByCause.merge(cause, seconds, Double::sum);
    }

    public void recordRouteRecalculation() {
        routeRecalculationCount++;
    }

    public void recordLegalUTurnCompleted() {
        legalUTurnsCompleted++;
    }

    public List<String> getVisitedIntersections() {
        return Collections.unmodifiableList(visitedIntersections);
    }

    public double getDistanceTravelledKm() {
        return distanceTravelledKm;
    }

    public double getTravelTimeSeconds() {
        return travelTimeSeconds;
    }

    public long getWaitingTicks() {
        return waitingTicks;
    }

    public double getWaitingSeconds() {
        return waitingSeconds;
    }

    public Map<WaitCause, Double> getWaitingSecondsByCause() {
        return Collections.unmodifiableMap(new EnumMap<>(waitingSecondsByCause));
    }

    public int getRouteRecalculationCount() {
        return routeRecalculationCount;
    }

    public int getLegalUTurnsCompleted() {
        return legalUTurnsCompleted;
    }
}
