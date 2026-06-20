package team.traffic.persistence;

import java.io.IOException;

import team.traffic.domain.network.RoadNetwork;

public interface MapLoader {
    // Loads a saved map file into a road network.
    RoadNetwork load(String filePath) throws IOException;
}
