package team.traffic.persistence;

import java.io.File;
import team.traffic.domain.network.RoadNetwork;

public class JsonMapLoader implements MapLoader {
    @Override
    public RoadNetwork load(File file) {
        // TODO: parse JSON map file into RoadNetwork.
        throw new UnsupportedOperationException("TODO: implement JSON map loading.");
    }

    @Override
    public boolean supports(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".json");
    }
}
