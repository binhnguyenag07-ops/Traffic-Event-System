package team.traffic.routing;

import team.traffic.domain.network.Intersection;

@FunctionalInterface
public interface Heuristic {
    double estimate(Intersection a, Intersection b);
}
