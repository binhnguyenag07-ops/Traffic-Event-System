package team.traffic.service.impl;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoadNetworkService;

public class RoadNetworkServiceImpl implements RoadNetworkService {
    private final RoadNetwork network;

    public RoadNetworkServiceImpl(RoadNetwork network) {
        if (network == null) {
            throw new IllegalArgumentException("Road network is required");
        }
        this.network = network;
    }

    @Override
    public RoadNetwork getNetwork() {
        return network;
    }
}
