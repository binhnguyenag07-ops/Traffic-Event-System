package team.traffic.routing;

import team.traffic.domain.network.Intersection;

public interface Heuristic {
    double estimate(Intersection current, Intersection destination);
}
