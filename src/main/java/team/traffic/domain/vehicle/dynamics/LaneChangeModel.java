package team.traffic.domain.vehicle.dynamics;

/** Chooses a lane during the planning half of a simulation sub-step. */
public interface LaneChangeModel {
    int chooseTargetLane(LaneChangeRequest request);

    String getName();
}
