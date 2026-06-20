package team.traffic.service;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;

public interface RoutingService {
    RouteResult findRoute(RoadNetwork network, RouteRequest request);
}
