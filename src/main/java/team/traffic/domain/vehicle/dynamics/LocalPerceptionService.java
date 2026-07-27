package team.traffic.domain.vehicle.dynamics;

import team.traffic.domain.vehicle.Vehicle;

public interface LocalPerceptionService {
    LocalPerception observe(Vehicle vehicle, TrafficState state);
}
