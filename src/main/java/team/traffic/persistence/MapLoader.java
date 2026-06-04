package team.traffic.persistence;

import java.io.File;
import team.traffic.domain.network.RoadNetwork;

public interface MapLoader {
    RoadNetwork load(File file);

    boolean supports(File file);
}
