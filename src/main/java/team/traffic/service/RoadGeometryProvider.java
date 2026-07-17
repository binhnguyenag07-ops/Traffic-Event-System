package team.traffic.service;

import java.util.List;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;

public interface RoadGeometryProvider {
    RoadGeometryResult route(Intersection start, Intersection end);

    record RoadGeometryResult(List<GeoPoint> geometry, double distanceKilometres, String source) {
        public RoadGeometryResult {
            geometry = List.copyOf(geometry);
        }
    }
}
