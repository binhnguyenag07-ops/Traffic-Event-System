package team.traffic.persistence;

import java.io.File;
import team.traffic.domain.network.RoadNetwork;

public class CsvMapLoader implements MapLoader {
    @Override
    public RoadNetwork load(File file) {
        // TODO: parse CSV rows from data/sample_map.csv into intersections and roads.
        throw new UnsupportedOperationException("TODO: implement CSV map loading.");
    }

    @Override
    public boolean supports(File file) {
        return file != null && file.getName().toLowerCase().endsWith(".csv");
    }
}
