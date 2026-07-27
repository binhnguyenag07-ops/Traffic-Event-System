package team.traffic.simulation.navigation;

import team.traffic.domain.routing.RouteCost;
import team.traffic.domain.vehicle.dynamics.DriverProfile;
import team.traffic.domain.vehicle.dynamics.LocalPerception;
import team.traffic.domain.vehicle.navigation.DriverKnowledge;
import team.traffic.domain.vehicle.navigation.NavigationState;
import team.traffic.domain.vehicle.navigation.StallState;

public record DriverContext(
        LocalPerception local,
        DriverKnowledge knowledge,
        NavigationState navigation,
        StallState stall,
        RouteCost currentRemainingCost,
        DriverProfile profile,
        double simulationSeconds) {

    public DriverContext {
        if (local == null || knowledge == null || navigation == null || stall == null
                || currentRemainingCost == null || profile == null
                || !Double.isFinite(simulationSeconds) || simulationSeconds < 0) {
            throw new IllegalArgumentException("Complete driver context is required.");
        }
    }
}
