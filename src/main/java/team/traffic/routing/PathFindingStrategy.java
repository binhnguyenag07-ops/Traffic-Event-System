package team.traffic.routing;

import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.routing.RoutePreference;
import team.traffic.domain.routing.RouteRequest;
import team.traffic.domain.routing.RouteResult;

public interface PathFindingStrategy {
    String getName();

    boolean supports(RoutePreference preference);

    RouteResult findRoute(RoadNetwork network, RouteRequest request);
}
