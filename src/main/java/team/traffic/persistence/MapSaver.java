package team.traffic.persistence;

import java.io.File;
import team.traffic.domain.network.RoadNetwork;

public interface MapSaver {
    void save(RoadNetwork roadNetwork, File file);
}
