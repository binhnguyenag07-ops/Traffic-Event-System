package team.traffic.simulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.VehicleStatus;
import team.traffic.domain.vehicle.dynamics.GapAcceptanceModel;
import team.traffic.domain.vehicle.dynamics.MovementPlan;
import team.traffic.domain.vehicle.dynamics.PriorityRightOfWayPolicy;
import team.traffic.domain.vehicle.dynamics.RightOfWayPolicy;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.event.TrafficEvent;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.signal.TrafficSignal;

/**
 * Drives one simulation step of the microscopic vehicle model.
 *
 * <p>Each step runs in three phases, which is what keeps the result from
 * depending on the order the vehicles happen to sit in the list:</p>
 * <ol>
 *   <li><b>Observe</b> — capture where every vehicle physically is;</li>
 *   <li><b>Arbitrate</b> — collect everyone waiting at a junction and let each
 *       junction decide, all at once, who may cross;</li>
 *   <li><b>Move</b> — every vehicle follows its leader and respects the
 *       decision made in phase two.</li>
 * </ol>
 *
 * <p>A tick is normally ten simulated seconds, which is far too coarse for a
 * car-following model tuned to a reaction time of about one second: a vehicle
 * would overshoot its leader and the junction would only be arbitrated once
 * while several vehicles' worth of traffic passed through it. The engine
 * therefore splits the tick into sub-steps of at most
 * {@value #MAX_SUB_STEP_SECONDS} seconds and runs all three phases in each.</p>
 */
public class VehicleDynamicsEngine {

    /** Longest sub-step the car-following model is integrated over, in seconds. */
    public static final double MAX_SUB_STEP_SECONDS = 1.0;
    /** Upper bound on sub-steps per tick, so a very long tick stays affordable. */
    public static final int MAX_SUB_STEPS_PER_TICK = 60;

    private final RightOfWayPolicy rightOfWayPolicy;
    private final GapAcceptanceModel gapAcceptanceModel;

    public VehicleDynamicsEngine() {
        this(new PriorityRightOfWayPolicy(), new GapAcceptanceModel());
    }

    public VehicleDynamicsEngine(RightOfWayPolicy rightOfWayPolicy, GapAcceptanceModel gapAcceptanceModel) {
        if (rightOfWayPolicy == null) {
            throw new IllegalArgumentException("Right-of-way policy is required.");
        }
        if (gapAcceptanceModel == null) {
            throw new IllegalArgumentException("Gap-acceptance model is required.");
        }
        this.rightOfWayPolicy = rightOfWayPolicy;
        this.gapAcceptanceModel = gapAcceptanceModel;
    }

    /**
     * Advances every vehicle over {@code simulationSeconds} of simulated time
     * and returns the traffic state of the final sub-step, which callers can
     * read for physical road occupancy.
     */
    public TrafficState step(
            long tick,
            RoadNetwork network,
            List<Vehicle> vehicles,
            double simulationSeconds) {
        return step(tick, tick, network, vehicles, List.of(), simulationSeconds);
    }

    public TrafficState step(
            long tick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            double simulationSeconds) {
        return step(tick, elapsedSeconds, network, vehicles, events, List.of(), simulationSeconds);
    }

    public TrafficState step(
            long tick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals,
            double simulationSeconds) {
        return step(tick, elapsedSeconds, network, vehicles, events, signals,
                simulationSeconds, (ignoredSeconds, ignoredState) -> {});
    }

    /**
     * Advances the microscopic model and exposes an immutable observation point
     * after every physical sub-step. The callback receives the exact simulated
     * second and a freshly captured post-movement state, so replay does not need
     * to interpolate vehicle positions later.
     */
    public TrafficState step(
            long tick,
            double elapsedSeconds,
            RoadNetwork network,
            List<Vehicle> vehicles,
            List<TrafficEvent> events,
            List<TrafficSignal> signals,
            double simulationSeconds,
            BiConsumer<Double, TrafficState> subStepObserver) {
        if (network == null) {
            throw new IllegalArgumentException("Road network is required.");
        }
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }
        if (subStepObserver == null) {
            throw new IllegalArgumentException("Sub-step observer is required.");
        }

        // Preserve the original extension point. A custom engine written before
        // frame replay may only override the seven-argument method; it cannot
        // expose internal sub-steps, but its final state is still a valid frame.
        if (overridesLegacyStep()) {
            TrafficState finalState = step(tick, elapsedSeconds, network, vehicles,
                    events, signals, simulationSeconds);
            subStepObserver.accept(elapsedSeconds + simulationSeconds, finalState);
            return finalState;
        }

        int subSteps = subStepCount(simulationSeconds);
        double subStepSeconds = simulationSeconds / subSteps;

        TrafficState state = null;
        for (int index = 0; index < subSteps; index++) {
            state = TrafficState.capture(
                    tick,
                    elapsedSeconds + index * subStepSeconds,
                    network,
                    vehicles,
                    events,
                    signals,
                    rightOfWayPolicy,
                    gapAcceptanceModel,
                    new RoadDataRoadLayout());
            arbitrateJunctions(network, vehicles, state);
            moveVehicles(network, vehicles, state, subStepSeconds);
            double stepEndSeconds = elapsedSeconds + (index + 1) * subStepSeconds;
            state = TrafficState.capture(
                    tick,
                    stepEndSeconds,
                    network,
                    vehicles,
                    events,
                    signals,
                    rightOfWayPolicy,
                    gapAcceptanceModel,
                    new RoadDataRoadLayout());
            subStepObserver.accept(stepEndSeconds, state);
        }
        return state;
    }

    private boolean overridesLegacyStep() {
        try {
            return getClass().getMethod(
                    "step",
                    long.class,
                    double.class,
                    RoadNetwork.class,
                    List.class,
                    List.class,
                    List.class,
                    double.class).getDeclaringClass() != VehicleDynamicsEngine.class;
        } catch (NoSuchMethodException impossible) {
            throw new IllegalStateException("Vehicle dynamics step contract is unavailable.", impossible);
        }
    }

    static int subStepCount(double simulationSeconds) {
        int required = (int) Math.ceil(simulationSeconds / MAX_SUB_STEP_SECONDS);
        return Math.min(MAX_SUB_STEPS_PER_TICK, Math.max(1, required));
    }

    private void arbitrateJunctions(RoadNetwork network, List<Vehicle> vehicles, TrafficState state) {
        for (Vehicle vehicle : vehicles) {
            if (isDormant(vehicle)) {
                continue;
            }
            vehicle.planJunctionApproach(network, state).ifPresent(state::requestCrossing);
        }
        state.resolveJunctions();
    }

    private void moveVehicles(
            RoadNetwork network,
            List<Vehicle> vehicles,
            TrafficState state,
            double subStepSeconds) {
        Map<Vehicle, MovementPlan> plans = new LinkedHashMap<>();
        for (Vehicle vehicle : vehicles) {
            if (isDormant(vehicle)) {
                continue;
            }
            vehicle.planMovement(network, state, subStepSeconds)
                    .ifPresent(plan -> plans.put(vehicle, plan));
        }
        plans.forEach((vehicle, plan) -> vehicle.commitMovement(plan, network, state));
    }

    /** A vehicle that has finished its trip takes no further part in the step. */
    private static boolean isDormant(Vehicle vehicle) {
        return vehicle.hasReachedDestination()
                || vehicle.getStatus() == VehicleStatus.ARRIVED
                || vehicle.getStatus() == VehicleStatus.STOPPED;
    }
}
