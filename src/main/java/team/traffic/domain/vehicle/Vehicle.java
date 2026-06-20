package team.traffic.domain.vehicle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.Route;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;
import team.traffic.domain.routing.RouteStep;
import team.traffic.service.RoutingService;

public abstract class Vehicle {
    private final String id;
    private String currentIntersectionId;
    private final String destinationIntersectionId;
    private Route currentRoute;
    private int nextRouteStepIndex;
    private RoutePreference routePreference;
    private double movementSpeed;
    private VehicleStatus status;
    private final List<String> travelHistory;

    protected Vehicle(String id, String currentIntersectionId, String destinationIntersectionId, double movementSpeed) {
        this.id = id;
        this.currentIntersectionId = currentIntersectionId;
        this.destinationIntersectionId = destinationIntersectionId;
        this.movementSpeed = movementSpeed;
        this.routePreference = RoutePreference.SHORTEST_TIME;
        this.status = VehicleStatus.WAITING;
        this.travelHistory = new ArrayList<>();
        this.travelHistory.add(currentIntersectionId);
    }

    public abstract VehicleType getType();

    public void requestRoute(RoutingService routingService) {
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        this.currentRoute = null;
        this.nextRouteStepIndex = 0;
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.WAITING;
    }

    public void requestRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        requestRoute(roadNetwork, routingService, routePreference);
    }

    public void requestRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        Objects.requireNonNull(roadNetwork, "Road network must not be null.");
        Objects.requireNonNull(routingService, "Routing service must not be null.");
        Objects.requireNonNull(preference, "Route preference must not be null.");

        routePreference = preference;
        nextRouteStepIndex = 0;

        if (hasReachedDestination()) {
            currentRoute = null;
            status = VehicleStatus.ARRIVED;
            return;
        }

        RouteRequest request = new RouteRequest(
                currentIntersectionId,
                destinationIntersectionId,
                preference);

        try {
            RouteResult result = routingService.findRoute(roadNetwork, request);
            currentRoute = result == null ? null : result.getRoute();
            status = isUsableRoute(currentRoute) ? VehicleStatus.WAITING : VehicleStatus.BLOCKED;
        } catch (NoSuchElementException exception) {
            currentRoute = null;
            status = VehicleStatus.BLOCKED;
        }
    }

    public void moveOneStep() {
        moveOneStep(null);
    }

    public void moveOneStep(RoadNetwork roadNetwork) {
        if (hasReachedDestination()) {
            this.status = VehicleStatus.ARRIVED;
            return;
        }

        RouteStep nextStep = getNextRouteStep();
        if (nextStep == null || !nextStep.getFromIntersectionId().equals(currentIntersectionId)) {
            status = VehicleStatus.BLOCKED;
            return;
        }

        if (roadNetwork != null && !isStepAvailable(roadNetwork, nextStep)) {
            status = VehicleStatus.BLOCKED;
            return;
        }

        nextRouteStepIndex++;
        moveTo(nextStep.getToIntersectionId());

        if (!hasReachedDestination() && nextRouteStepIndex >= currentRoute.getStepCount()) {
            status = VehicleStatus.BLOCKED;
        }
    }

    public void recomputeRoute(RoutingService routingService) {
        requestRoute(routingService);
    }

    public void recomputeRoute(RoadNetwork roadNetwork, RoutingService routingService) {
        requestRoute(roadNetwork, routingService, routePreference);
    }

    public void recomputeRoute(
            RoadNetwork roadNetwork,
            RoutingService routingService,
            RoutePreference preference) {
        requestRoute(roadNetwork, routingService, preference);
    }

    public void moveTo(String nextIntersectionId) {
        if (nextIntersectionId == null || nextIntersectionId.isBlank()) {
            throw new IllegalArgumentException("Next intersection id must not be blank.");
        }

        this.currentIntersectionId = nextIntersectionId;
        this.travelHistory.add(nextIntersectionId);
        this.status = hasReachedDestination() ? VehicleStatus.ARRIVED : VehicleStatus.MOVING;
    }

    public boolean hasReachedDestination() {
        return destinationIntersectionId.equals(currentIntersectionId);
    }

    public String getId() {
        return id;
    }

    public String getCurrentIntersectionId() {
        return currentIntersectionId;
    }

    public String getDestinationIntersectionId() {
        return destinationIntersectionId;
    }

    public Route getCurrentRoute() {
        return currentRoute;
    }

    public void setCurrentRoute(Route currentRoute) {
        this.currentRoute = currentRoute;
        this.nextRouteStepIndex = 0;
        if (hasReachedDestination()) {
            this.status = VehicleStatus.ARRIVED;
        } else {
            this.status = isUsableRoute(currentRoute) ? VehicleStatus.WAITING : VehicleStatus.BLOCKED;
        }
    }

    public int getNextRouteStepIndex() {
        return nextRouteStepIndex;
    }

    public RoutePreference getRoutePreference() {
        return routePreference;
    }

    public double getMovementSpeed() {
        return movementSpeed;
    }

    public void setMovementSpeed(double movementSpeed) {
        if (movementSpeed <= 0) {
            throw new IllegalArgumentException("Movement speed must be positive.");
        }
        this.movementSpeed = movementSpeed;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public List<String> getTravelHistory() {
        return Collections.unmodifiableList(travelHistory);
    }

    private RouteStep getNextRouteStep() {
        if (currentRoute == null || nextRouteStepIndex >= currentRoute.getStepCount()) {
            return null;
        }
        return currentRoute.getSteps().get(nextRouteStepIndex);
    }

    private boolean isUsableRoute(Route route) {
        if (route == null || route.isEmpty()) {
            return false;
        }

        List<RouteStep> steps = route.getSteps();
        return steps.get(0).getFromIntersectionId().equals(currentIntersectionId)
                && steps.get(steps.size() - 1).getToIntersectionId().equals(destinationIntersectionId);
    }

    private boolean isStepAvailable(RoadNetwork roadNetwork, RouteStep step) {
        Road road = roadNetwork.getRoad(step.getRoadId());
        return road != null
                && road.getStartIntersectionId().equals(step.getFromIntersectionId())
                && road.getEndIntersectionId().equals(step.getToIntersectionId())
                && road.isAvailable();
    }
}
