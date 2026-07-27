package team.traffic.simulation.history;

import java.util.List;
import java.util.Set;
import team.traffic.domain.event.AccidentEvent;
import team.traffic.domain.event.BreakdownEvent;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.event.TrafficEventStatus;
import team.traffic.domain.event.TrafficEventType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.signal.SignalPhase;
import team.traffic.domain.signal.TrafficSignal;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.pose.VehiclePose;
import team.traffic.domain.vehicle.pose.VehiclePoseResolver;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.navigation.NavigationDecisionRecord;
import team.traffic.domain.vehicle.navigation.NavigationMode;
import team.traffic.simulation.SimulationStatus;

/**
 * Immutable, renderer-ready state captured at one authoritative simulation time.
 * Both the macro map and the future physical view must render the same frame id.
 */
public record SimulationFrame(
        String timelineId,
        long frameId,
        long tick,
        double elapsedSeconds,
        SimulationStatus status,
        List<VehicleState> vehicles,
        List<EventState> trafficEvents,
        List<RoadState> roadStates,
        List<SignalState> trafficSignals) {

    public SimulationFrame {
        if (timelineId == null || timelineId.isBlank()) {
            throw new IllegalArgumentException("Timeline id is required.");
        }
        if (frameId < 0 || tick < 0 || !Double.isFinite(elapsedSeconds) || elapsedSeconds < 0) {
            throw new IllegalArgumentException("Frame id, tick, and elapsed seconds must not be negative.");
        }
        if (status == null) throw new IllegalArgumentException("Simulation status is required.");
        vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
        trafficEvents = trafficEvents == null ? List.of() : List.copyOf(trafficEvents);
        roadStates = roadStates == null ? List.of() : List.copyOf(roadStates);
        trafficSignals = trafficSignals == null ? List.of() : List.copyOf(trafficSignals);
    }

    public static SimulationFrame capture(
            String timelineId,
            long frameId,
            long tick,
            double elapsedSeconds,
            SimulationStatus status,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals) {
        if (network == null) throw new IllegalArgumentException("Road network is required.");
        VehiclePoseResolver poseResolver = new VehiclePoseResolver();
        // Resolved together, not one by one: vehicles waiting at a junction have
        // to be strung out along their approach, and where one sits in that
        // queue depends on the others.
        var poses = poseResolver.resolveAll(vehicles, network);
        return new SimulationFrame(
                timelineId,
                frameId,
                tick,
                elapsedSeconds,
                status,
                vehicles == null ? List.of() : vehicles.stream()
                        .map(vehicle -> VehicleState.from(vehicle,
                                poses.get(vehicle.getId()),
                                elapsedSeconds))
                        .toList(),
                events == null ? List.of() : events.stream().map(EventState::from).toList(),
                network.getRoads().values().stream().map(RoadState::from).toList(),
                signals == null ? List.of() : signals.stream()
                        .map(signal -> SignalState.from(signal, elapsedSeconds))
                        .toList());
    }

    public record VehicleState(
            String id,
            VehicleType type,
            VehicleStatus status,
            String currentIntersectionId,
            String destinationIntersectionId,
            String occupiedRoadId,
            String roadStartIntersectionId,
            String roadEndIntersectionId,
            double roadProgress,
            double currentSpeed,
            double instantaneousSpeed,
            int laneIndex,
            double length,
            double width,
            Double latitude,
            Double longitude,
            Double headingDegrees,
            boolean inJunction,
            double lateralOffsetMetres,
            String junctionFromRoadId,
            String junctionToRoadId,
            WaitCause waitCause,
            double waitCauseSeconds,
            String blockingResource,
            NavigationMode navigationMode,
            long knowledgeVersion,
            long routeVersion,
            String lastRerouteReason,
            Double lastRerouteAtSeconds,
            double nextStrategicDecisionAtSeconds,
            String nextPlannedMovement,
            int legalUTurnsCompleted,
            NavigationDecisionRecord lastNavigationDecision) {

        static VehicleState from(
                Vehicle vehicle,
                VehiclePose pose,
                double elapsedSeconds) {
            boolean inJunction = pose != null && pose.inJunction();
            return new VehicleState(
                    vehicle.getId(),
                    vehicle.getType(),
                    vehicle.getStatus(),
                    vehicle.getCurrentIntersectionId(),
                    vehicle.getDestinationIntersectionId(),
                    vehicle.getOccupiedRoadId(),
                    vehicle.getRoadStartIntersectionId(),
                    vehicle.getRoadEndIntersectionId(),
                    vehicle.getRoadProgress(),
                    vehicle.getCruisingSpeedKph(),
                    vehicle.getInstantaneousSpeedKph(),
                    pose == null ? vehicle.getLaneIndex() : pose.laneIndex(),
                    vehicle.getLength(),
                    vehicle.getWidth(),
                    pose == null ? null : pose.latitude(),
                    pose == null ? null : pose.longitude(),
                    pose == null ? null : pose.headingDegrees(),
                    inJunction,
                    pose == null ? 0.0 : pose.lateralOffsetMetres(),
                    inJunction ? routeRoadIdAt(vehicle, -1) : null,
                    inJunction ? routeRoadIdAt(vehicle, 0) : null,
                    vehicle.getWaitState().cause(),
                    vehicle.getWaitState().isWaiting()
                            ? Math.max(
                                    0.0,
                                    elapsedSeconds
                                            - vehicle.getWaitState().sinceSeconds())
                            : 0.0,
                    vehicle.getWaitState().resource()
                            .map(resource -> resource.stableKey())
                            .orElse(null),
                    vehicle.getNavigationState().getMode(),
                    vehicle.getDriverKnowledge().getVersion(),
                    vehicle.getRouteVersion(),
                    vehicle.getNavigationState().getLastRerouteReason(),
                    Double.isFinite(
                            vehicle.getNavigationState()
                                    .getLastRerouteAtSeconds())
                            ? vehicle.getNavigationState()
                                    .getLastRerouteAtSeconds()
                            : null,
                    vehicle.getNavigationState()
                            .getNextStrategicDecisionAtSeconds(),
                    vehicle.getNextRoadId(),
                    vehicle.getLegalUTurnsCompleted(),
                    vehicle.getNavigationState().getLastDecision());
        }

        private static String routeRoadIdAt(Vehicle vehicle, int offset) {
            var route = vehicle.getCurrentRoute();
            int index = vehicle.getNextRouteStepIndex() + offset;
            if (route == null || index < 0 || index >= route.getStepCount()) {
                return null;
            }
            return route.getSteps().get(index).getRoadId();
        }
    }

    public record EventState(
            String id,
            TrafficEventType type,
            String affectedRoadId,
            double startTimeSeconds,
            double durationSeconds,
            double positionAlongRoad,
            Set<Integer> blockedLaneIndexes,
            String causingVehicleId,
            String affectedVehicleId,
            Set<String> involvedVehicleIds,
            TrafficEventStatus status) {

        public EventState {
            blockedLaneIndexes = blockedLaneIndexes == null ? Set.of() : Set.copyOf(blockedLaneIndexes);
            involvedVehicleIds = involvedVehicleIds == null ? Set.of() : Set.copyOf(involvedVehicleIds);
        }

        static EventState from(TrafficEvent event) {
            String causingVehicleId = null;
            String affectedVehicleId = null;
            Set<String> involvedVehicleIds = Set.of();
            if (event instanceof AccidentEvent accident) {
                causingVehicleId = accident.getCausingVehicleId();
                involvedVehicleIds = accident.getInvolvedVehicleIds();
            }
            if (event instanceof BreakdownEvent breakdown) {
                affectedVehicleId = breakdown.getAffectedVehicleId();
            }
            return new EventState(
                    event.getId(), event.getType(), event.getAffectedRoadId(),
                    event.getStartTimeSeconds(), event.getDurationSeconds(),
                    event.getPositionAlongRoad(), event.getBlockedLaneIndexes(),
                    causingVehicleId, affectedVehicleId, involvedVehicleIds, event.getStatus());
        }
    }

    public record RoadState(
            String id,
            String status,
            double congestionLevel,
            double vehicleDensity,
            int currentVehicleCount,
            int capacity,
            boolean available,
            double travelCost) {

        static RoadState from(Road road) {
            return new RoadState(
                    road.getId(),
                    road.getCondition().getRoadStatus().name(),
                    road.getCondition().getCongestionLevel(),
                    road.getCondition().getVehicleDensity(),
                    road.getCurrentVehicleCount(),
                    road.getCapacity(),
                    road.isAvailable(),
                    road.calculateTravelCost());
        }
    }

    public record SignalState(
            String junctionId,
            List<SignalPhase> phases,
            double offsetSeconds,
            double cycleDurationSeconds,
            int activePhaseIndex,
            Set<String> greenIncomingRoadIds,
            double phaseRemainingSeconds) {

        public SignalState {
            phases = phases == null ? List.of() : List.copyOf(phases);
            greenIncomingRoadIds = greenIncomingRoadIds == null
                    ? Set.of() : Set.copyOf(greenIncomingRoadIds);
        }

        static SignalState from(TrafficSignal signal, double elapsedSeconds) {
            double withinCycle = (elapsedSeconds + signal.getOffsetSeconds())
                    % signal.getCycleDurationSeconds();
            List<SignalPhase> phases = signal.getPhases();
            for (int index = 0; index < phases.size(); index++) {
                SignalPhase phase = phases.get(index);
                if (withinCycle < phase.durationSeconds()) {
                    return new SignalState(
                            signal.getJunctionId(), phases, signal.getOffsetSeconds(),
                            signal.getCycleDurationSeconds(), index,
                            phase.greenIncomingRoadIds(), phase.durationSeconds() - withinCycle);
                }
                withinCycle -= phase.durationSeconds();
            }
            SignalPhase fallback = phases.get(phases.size() - 1);
            return new SignalState(
                    signal.getJunctionId(), phases, signal.getOffsetSeconds(),
                    signal.getCycleDurationSeconds(), phases.size() - 1,
                    fallback.greenIncomingRoadIds(), fallback.durationSeconds());
        }
    }
}
