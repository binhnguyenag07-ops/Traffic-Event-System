package team.traffic.persistence;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.exception.InvalidMapException;

public class CsvMapLoader implements MapLoader {
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "type",
            "id",
            "name",
            "x",
            "y",
            "locationType",
            "startIntersectionId",
            "endIntersectionId",
            "distance",
            "speedLimit",
            "capacity",
            "currentVehicleCount",
            "congestionLevel",
            "vehicleDensity",
            "roadStatus");

    // Reads all records first so ROAD rows may appear before INTERSECTION rows.
    @Override
    public RoadNetwork load(String filePath) throws IOException {
        Path path = requireFilePath(filePath);
        List<CSVRecord> intersectionRecords = new ArrayList<>();
        List<CSVRecord> roadRecords = new ArrayList<>();

        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
                CSVParser parser = CSVFormat.DEFAULT.builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setTrim(true)
                        .setIgnoreEmptyLines(true)
                        .build()
                        .parse(reader)) {
            validateHeaders(parser);
            classifyRecords(parser, intersectionRecords, roadRecords);
        } catch (InvalidMapException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new InvalidMapException("Invalid CSV map structure: " + exception.getMessage(), exception);
        }

        if (intersectionRecords.isEmpty()) {
            throw new InvalidMapException("Map must contain at least one INTERSECTION record");
        }

        RoadNetwork roadNetwork = new RoadNetwork();
        addIntersections(roadNetwork, intersectionRecords);
        addRoads(roadNetwork, roadRecords);
        return roadNetwork;
    }

    private Path requireFilePath(String filePath) throws InvalidMapException {
        if (filePath == null || filePath.isBlank()) {
            throw new InvalidMapException("Map file path is required");
        }
        return Path.of(filePath);
    }

    private void validateHeaders(CSVParser parser) throws InvalidMapException {
        Set<String> actualHeaders = parser.getHeaderMap().keySet();
        List<String> missingHeaders = REQUIRED_HEADERS.stream()
                .filter(header -> !actualHeaders.contains(header))
                .sorted()
                .toList();
        if (!missingHeaders.isEmpty()) {
            throw new InvalidMapException("CSV map is missing required headers: " + missingHeaders);
        }
    }

    private void classifyRecords(
            CSVParser parser,
            List<CSVRecord> intersectionRecords,
            List<CSVRecord> roadRecords) throws InvalidMapException {
        for (CSVRecord record : parser) {
            String type = record.get("type").toUpperCase(Locale.ROOT);
            switch (type) {
                case "INTERSECTION" -> intersectionRecords.add(record);
                case "ROAD" -> roadRecords.add(record);
                default -> throw new InvalidMapException(
                        "Unsupported CSV record type at record "
                                + record.getRecordNumber() + ": " + record.get("type"));
            }
        }
    }

    private void addIntersections(RoadNetwork roadNetwork, List<CSVRecord> records)
            throws InvalidMapException {
        for (CSVRecord record : records) {
            try {
                roadNetwork.addIntersection(createIntersection(record));
            } catch (RuntimeException exception) {
                throw invalidRecord("INTERSECTION", record, exception);
            }
        }
    }

    private void addRoads(RoadNetwork roadNetwork, List<CSVRecord> records)
            throws InvalidMapException {
        for (CSVRecord record : records) {
            try {
                roadNetwork.addRoad(createRoad(record));
            } catch (RuntimeException exception) {
                throw invalidRecord("ROAD", record, exception);
            }
        }
    }

    private InvalidMapException invalidRecord(
            String type,
            CSVRecord record,
            RuntimeException cause) {
        return new InvalidMapException(
                "Invalid " + type + " record " + record.getRecordNumber() + ": " + cause.getMessage(),
                cause);
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
