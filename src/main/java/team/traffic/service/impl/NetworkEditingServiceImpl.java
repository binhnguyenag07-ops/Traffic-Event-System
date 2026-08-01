package team.traffic.service.impl;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.exception.NetworkEditConflictException;
import team.traffic.persistence.MapSaver;
import team.traffic.service.NetworkEditingService;
import team.traffic.service.RoadGeometryProvider;
import team.traffic.service.RoutingService;
import team.traffic.service.SimulationService;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.TrafficSimulator;

public class NetworkEditingServiceImpl implements NetworkEditingService {
    private static final double HCMC_REFERENCE_LATITUDE = 10.774;
    private static final double HCMC_REFERENCE_LONGITUDE = 106.701;
    private final TrafficSimulator simulator;
    private final SimulationService simulationService;
    private final RoutingService routingService;
    private final RoadGeometryProvider geometryProvider;
    private final MapSaver mapSaver;

    public NetworkEditingServiceImpl(TrafficSimulator simulator, SimulationService simulationService,
            RoutingService routingService, RoadGeometryProvider geometryProvider) {
        this.simulator = simulator;
        this.simulationService = simulationService;
        this.routingService = routingService;
        this.geometryProvider = geometryProvider;
        this.mapSaver = new MapSaver();
    }

    @Override
    public Intersection addIntersection(String id, String name,
            double latitude, double longitude) {
        double y = (latitude - HCMC_REFERENCE_LATITUDE) * 110.574;
        double x = (longitude - HCMC_REFERENCE_LONGITUDE) * 111.320
                * Math.cos(Math.toRadians(latitude));
        Intersection intersection = new Intersection(id, name, x, y, latitude, longitude);
        synchronized (simulator) {
            requireEditable();
            simulator.getNetwork().addIntersection(intersection);
        }
        publish();
        return intersection;
    }

    @Override
    public void removeIntersection(String id) {
        synchronized (simulator) {
            requireEditable();
            RoadNetwork network = simulator.getNetwork();
            if (!network.containsIntersection(id)) throw new IllegalArgumentException("Intersection not found: " + id);
            for (Vehicle vehicle : simulator.getVehicles()) {
                if (id.equals(vehicle.getCurrentIntersectionId()) || id.equals(vehicle.getDestinationIntersectionId())) {
                    throw new NetworkEditConflictException("Intersection is used by vehicle " + vehicle.getId());
                }
            }
            Set<String> connectedIds = new LinkedHashSet<>();
            network.findIncomingRoads(id).forEach(road -> connectedIds.add(road.getId()));
            network.findOutgoingRoads(id).forEach(road -> connectedIds.add(road.getId()));
            connectedIds.forEach(this::requireRoadSafeToDelete);
            network.removeIntersection(id);
            rerouteWaitingVehicles();
        }
        publish();
    }

    @Override
    public RoadCreationOutcome addRoads(RoadCreationRequest request) {
        validateRoadRequest(request);
        RoadNetwork network = simulator.getNetwork();
        Intersection start = requireIntersection(network, request.startIntersectionId());
        Intersection end = requireIntersection(network, request.endIntersectionId());
        RoadGeometryProvider.RoadGeometryResult forward = geometryProvider.route(start, end);
        Road forwardRoad = createRoad(request.id(), start, end, request, forward);
        RoadGeometryProvider.RoadGeometryResult reverse = null;
        Road reverseRoad = null;
        if (request.bidirectional()) {
            reverse = geometryProvider.route(end, start);
            reverseRoad = createRoad(request.reverseRoadId(), end, start, request, reverse);
        }
        synchronized (simulator) {
            requireEditable();
            network = simulator.getNetwork();
            requireIntersection(network, request.startIntersectionId());
            requireIntersection(network, request.endIntersectionId());
            if (network.containsRoad(request.id())
                    || request.bidirectional() && network.containsRoad(request.reverseRoadId())) {
                throw new IllegalArgumentException("Road id already exists");
            }
            network.addRoad(forwardRoad);
            if (reverseRoad != null) network.addRoad(reverseRoad);
            rerouteWaitingVehicles();
        }
        publish();
        return new RoadCreationOutcome(reverseRoad == null ? List.of(forwardRoad) : List.of(forwardRoad, reverseRoad),
                reverse == null ? List.of(forward.source()) : List.of(forward.source(), reverse.source()));
    }

    @Override
    public void removeRoad(String id) {
        synchronized (simulator) {
            requireEditable();
            if (!simulator.getNetwork().containsRoad(id)) throw new IllegalArgumentException("Road not found: " + id);
            requireRoadSafeToDelete(id);
            simulator.getNetwork().removeRoad(id);
            rerouteWaitingVehicles();
        }
        publish();
    }

    @Override
    public Road updateRoadCondition(String id, RoadStatus status, double congestionLevel) {
        Road road;
        synchronized (simulator) {
            requireEditable();
            road = simulator.getNetwork().getRoad(id);
            if (road == null) throw new IllegalArgumentException("Road not found: " + id);
            boolean hasActiveEvent = simulator.getEvents().stream().anyMatch(event -> id.equals(event.getAffectedRoadId())
                    && event.getStatus() == TrafficEventStatus.ACTIVE);
            if (hasActiveEvent) throw new NetworkEditConflictException("Resolve the active road event before editing its condition");
            RoadCondition condition = new RoadCondition(congestionLevel,
                    road.getCondition().getVehicleDensity(), status);
            simulator.getNetwork().updateRoadCondition(id, condition);
            rerouteWaitingVehicles();
        }
        publish();
        return road;
    }

    @Override
    public ConnectedRoads findConnectedRoads(String intersectionId) {
        RoadNetwork network = simulator.getNetwork();
        requireIntersection(network, intersectionId);
        return new ConnectedRoads(network.findIncomingRoads(intersectionId), network.findOutgoingRoads(intersectionId));
    }

    @Override
    public String exportJson() throws IOException {
        synchronized (simulator) {
            return mapSaver.toJson(simulator.getNetwork());
        }
    }

    @Override
    public JunctionMovement upsertMovement(JunctionMovement movement) {
        synchronized (simulator) {
            requireEditable();
            simulator.getNetwork().getMovementGraph().register(movement);
        }
        publish();
        return movement;
    }

    @Override
    public boolean removeMovement(
            String junctionId,
            String fromRoadId,
            String toRoadId) {
        boolean removed;
        synchronized (simulator) {
            requireEditable();
            removed = simulator.getNetwork().getMovementGraph().remove(
                    junctionId, fromRoadId, toRoadId);
        }
        if (removed) publish();
        return removed;
    }

    private Road createRoad(String id, Intersection start, Intersection end, RoadCreationRequest request,
            RoadGeometryProvider.RoadGeometryResult geometry) {
        return new Road(id, request.name(), request.physicalRoadId(), start.getId(), end.getId(),
                geometry.distanceKilometres(), request.speedLimit(), request.capacity(), 0,
                request.laneCount(), request.laneWidthMetres(),
                new RoadCondition(0, 0, RoadStatus.OPEN), geometry.geometry(), request.roadClass());
    }

    private void validateRoadRequest(RoadCreationRequest request) {
        if (request == null) throw new IllegalArgumentException("Road request is required");
        if (request.id() == null || request.id().isBlank()) throw new IllegalArgumentException("Road id is required");
        if (request.startIntersectionId() == null || request.endIntersectionId() == null
                || request.startIntersectionId().equals(request.endIntersectionId())) {
            throw new IllegalArgumentException("Road endpoints must be different intersections");
        }
        if (!Double.isFinite(request.speedLimit()) || request.speedLimit() <= 0) {
            throw new IllegalArgumentException("Speed limit must be positive");
        }
        if (request.capacity() <= 0) throw new IllegalArgumentException("Capacity must be positive");
        if (request.laneCount() <= 0) throw new IllegalArgumentException("Lane count must be positive");
        if (!Double.isFinite(request.laneWidthMetres()) || request.laneWidthMetres() <= 0) {
            throw new IllegalArgumentException("Lane width must be positive");
        }
        if (request.bidirectional() && (request.reverseRoadId() == null || request.reverseRoadId().isBlank()
                || request.id().equals(request.reverseRoadId()))) {
            throw new IllegalArgumentException("A distinct reverse road id is required");
        }
    }

    private void requireEditable() {
        if (simulator.getStatus() == SimulationStatus.RUNNING) {
            throw new NetworkEditConflictException("Pause the simulation before editing the network");
        }
    }

    private Intersection requireIntersection(RoadNetwork network, String id) {
        Intersection intersection = network.getIntersection(id);
        if (intersection == null) throw new IllegalArgumentException("Intersection not found: " + id);
        return intersection;
    }

    private void requireRoadSafeToDelete(String roadId) {
        boolean occupied = simulator.getVehicles().stream().anyMatch(vehicle -> roadId.equals(vehicle.getOccupiedRoadId()));
        if (occupied) throw new NetworkEditConflictException("Road is currently occupied: " + roadId);
        boolean referenced = simulator.getEvents().stream().anyMatch(event -> roadId.equals(event.getAffectedRoadId())
                && event.getStatus() != TrafficEventStatus.RESOLVED);
        if (referenced) throw new NetworkEditConflictException("Road is referenced by an unresolved event: " + roadId);
    }

    private void rerouteWaitingVehicles() {
        RoadNetwork network = simulator.getNetwork();
        for (Vehicle vehicle : simulator.getVehicles()) {
            if (vehicle.getStatus() != VehicleStatus.ARRIVED && vehicle.getOccupiedRoadId() == null) {
                vehicle.recomputeRoute(network, routingService);
            }
        }
    }

    private void publish() {
        // Historical frames only retain dynamic road state, not an entire copy
        // of map geometry. An in-place map edit therefore begins a new timeline
        // instead of letting an old frame render against a different network.
        simulationService.resetFrameTimeline();
        simulationService.publishCurrentSnapshot();
    }
}
