package team.traffic.persistence;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

public class SyntheticMapGenerator {
    private static final double DEFAULT_ROAD_DISTANCE_KM = 1.0;
    private static final double DEFAULT_SPEED_LIMIT_KMH = 50.0;
    private static final int DEFAULT_ROAD_CAPACITY = 100;

    /**
     * Creates a rectangular grid where every horizontal or vertical neighbor
     * pair is connected by two directed roads.
     *
     * <p>Intersection coordinates use one grid unit per kilometre. Generated
     * roads therefore have a distance of 1 km and start open with no traffic.</p>
     */
    public RoadNetwork generateGridMap(int rows, int columns) {
        if (rows <= 0 || columns <= 0) {
            throw new IllegalArgumentException("Rows and columns must be greater than 0");
        }

        RoadNetwork roadNetwork = new RoadNetwork();
        addIntersections(roadNetwork, rows, columns);
        addRoads(roadNetwork, rows, columns);
        return roadNetwork;
    }

    // Adds intersections using predictable ids like I_0_0.
    private void addIntersections(RoadNetwork roadNetwork, int rows, int columns) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                roadNetwork.addIntersection(new Intersection(
                        intersectionId(row, column),
                        "Intersection " + row + "-" + column,
                        column,
                        row));
            }
        }
    }

    // Connects horizontal and vertical neighbors in both directions.
    private void addRoads(RoadNetwork roadNetwork, int rows, int columns) {
        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                if (column + 1 < columns) {
                    addBidirectionalRoads(roadNetwork, row, column, row, column + 1);
                }
                if (row + 1 < rows) {
                    addBidirectionalRoads(roadNetwork, row, column, row + 1, column);
                }
            }
        }
    }

    // Adds one road for each travel direction between two intersections.
    private void addBidirectionalRoads(RoadNetwork roadNetwork, int fromRow, int fromColumn, int toRow, int toColumn) {
        String fromId = intersectionId(fromRow, fromColumn);
        String toId = intersectionId(toRow, toColumn);
        roadNetwork.addRoad(createRoad(fromId + "_TO_" + toId, fromId, toId));
        roadNetwork.addRoad(createRoad(toId + "_TO_" + fromId, toId, fromId));
    }

    // Creates a normal open road with default traffic values.
    private Road createRoad(String id, String startIntersectionId, String endIntersectionId) {
        return new Road(
                id,
                startIntersectionId,
                endIntersectionId,
                DEFAULT_ROAD_DISTANCE_KM,
                DEFAULT_SPEED_LIMIT_KMH,
                DEFAULT_ROAD_CAPACITY,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }

    private String intersectionId(int row, int column) {
        return "I_" + row + "_" + column;
    }
}
