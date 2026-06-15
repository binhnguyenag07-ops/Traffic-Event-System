package team.traffic.persistence;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

public class CsvMapLoader implements MapLoader {

    // Reads INTERSECTION and ROAD rows from a CSV file.
    @Override
    public RoadNetwork load(String filePath) throws IOException {
        RoadNetwork roadNetwork = new RoadNetwork();

        try (Reader reader = Files.newBufferedReader(Path.of(filePath));
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .build()
                        .parse(reader)) {
            for (CSVRecord record : parser) {
                String type = record.get("type").toUpperCase();
                if ("INTERSECTION".equals(type)) {
                    roadNetwork.addIntersection(createIntersection(record));
                } else if ("ROAD".equals(type)) {
                    roadNetwork.addRoad(createRoad(record));
                }
            }
        }

        return roadNetwork;
    }

    // Creates an intersection from one CSV record.
    private Intersection createIntersection(CSVRecord record) {
        return new Intersection(
                record.get("id"),
                record.get("name"),
                Double.parseDouble(record.get("x")),
                Double.parseDouble(record.get("y")),
                LocationType.valueOf(record.get("locationType").toUpperCase()));
    }

    // Creates a road and its condition from one CSV record.
    private Road createRoad(CSVRecord record) {
        RoadCondition condition = new RoadCondition(
                Double.parseDouble(record.get("congestionLevel")),
                Double.parseDouble(record.get("vehicleDensity")),
                RoadStatus.valueOf(record.get("roadStatus").toUpperCase()));

        return new Road(
                record.get("id"),
                record.get("startIntersectionId"),
                record.get("endIntersectionId"),
                Double.parseDouble(record.get("distance")),
                Double.parseDouble(record.get("speedLimit")),
                Integer.parseInt(record.get("capacity")),
                Integer.parseInt(record.get("currentVehicleCount")),
                condition);
    }
}
