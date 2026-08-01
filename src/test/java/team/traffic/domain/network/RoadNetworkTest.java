package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoadNetworkTest {
    private RoadNetwork roadNetwork;

    @BeforeEach
    void setUp() {
        roadNetwork = new RoadNetwork();
        roadNetwork.addIntersection(intersection("I1", "First"));
        roadNetwork.addIntersection(intersection("I2", "Second"));
    }

    @Test
    void rejectsDuplicateIntersectionIdWithoutReplacingOriginal() {
        assertThatThrownBy(() -> roadNetwork.addIntersection(intersection("I1", "Replacement")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate intersection id: I1");

        assertThat(roadNetwork.getIntersections()).hasSize(2);
        assertThat(roadNetwork.getIntersection("I1").getName()).isEqualTo("First");
    }

    @Test
    void rejectsDuplicateRoadIdWithoutReplacingOriginal() {
        Road original = road("R1", 5);
        roadNetwork.addRoad(original);

        assertThatThrownBy(() -> roadNetwork.addRoad(road("R1", 10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Duplicate road id: R1");

        assertThat(roadNetwork.getRoads()).hasSize(1);
        assertThat(roadNetwork.getRoad("R1")).isSameAs(original);
        assertThat(roadNetwork.getRoad("R1").getDistance()).isEqualTo(5);
    }

    @Test
    void keepsEndpointValidationForNewRoads() {
        Road missingStart = new Road(
                "R1", "UNKNOWN", "I2", 5, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN));

        assertThatThrownBy(() -> roadNetwork.addRoad(missingStart))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Start intersection does not exist: UNKNOWN");
    }

    @Test
    void indexesIncomingAndOutgoingRoadsWhenRoadIsAdded() {
        Road road = road("R1", 5);

        roadNetwork.addRoad(road);

        assertThat(roadNetwork.findOutgoingRoads("I1")).containsExactly(road);
        assertThat(roadNetwork.findIncomingRoads("I2")).containsExactly(road);
        assertThat(roadNetwork.findIncomingRoads("I1")).isEmpty();
        assertThat(roadNetwork.findOutgoingRoads("I2")).isEmpty();
    }

    @Test
    void removesRoadFromBothIndexes() {
        Road road = road("R1", 5);
        roadNetwork.addRoad(road);

        roadNetwork.removeRoad("R1");

        assertThat(roadNetwork.containsRoad("R1")).isFalse();
        assertThat(roadNetwork.findOutgoingRoads("I1")).isEmpty();
        assertThat(roadNetwork.findIncomingRoads("I2")).isEmpty();
    }

    @Test
    void removingIntersectionRemovesEveryConnectedRoadFromIndexes() {
        roadNetwork.addIntersection(intersection("I3", "Third"));
        roadNetwork.addRoad(road("R1", "I1", "I2"));
        roadNetwork.addRoad(road("R2", "I2", "I1"));
        roadNetwork.addRoad(road("R3", "I3", "I1"));
        roadNetwork.addRoad(road("R4", "I2", "I3"));

        roadNetwork.removeIntersection("I1");

        assertThat(roadNetwork.containsIntersection("I1")).isFalse();
        assertThat(roadNetwork.getRoads()).containsOnlyKeys("R4");
        assertThat(roadNetwork.findOutgoingRoads("I2"))
                .extracting(Road::getId)
                .containsExactly("R4");
        assertThat(roadNetwork.findIncomingRoads("I3"))
                .extracting(Road::getId)
                .containsExactly("R4");
        assertThat(roadNetwork.findIncomingRoads("I1")).isEmpty();
        assertThat(roadNetwork.findOutgoingRoads("I1")).isEmpty();
    }

    @Test
    void handlesSelfLoopWhenIntersectionIsRemoved() {
        Road selfLoop = road("R1", "I1", "I1");
        roadNetwork.addRoad(selfLoop);

        roadNetwork.removeIntersection("I1");

        assertThat(roadNetwork.containsRoad("R1")).isFalse();
        assertThat(roadNetwork.getRoadCount()).isZero();
    }

    @Test
    void returnsImmutableRoadIndexViews() {
        Road road = road("R1", 5);
        roadNetwork.addRoad(road);

        List<Road> outgoing = roadNetwork.findOutgoingRoads("I1");
        List<Road> incoming = roadNetwork.findIncomingRoads("I2");

        assertThatThrownBy(() -> outgoing.add(road("R2", 10)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(incoming::clear)
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void returnsUniqueOutgoingNeighborsInRoadInsertionOrder() {
        roadNetwork.addIntersection(intersection("I3", "Third"));
        roadNetwork.addRoad(road("R1", "I1", "I2"));
        roadNetwork.addRoad(road("R2", "I1", "I3"));
        roadNetwork.addRoad(road("R3", "I1", "I2"));

        assertThat(roadNetwork.findOutgoingNeighbors("I1"))
                .extracting(Intersection::getId)
                .containsExactly("I2", "I3");
    }

    @Test
    void exposesGraphMembershipAndCounts() {
        roadNetwork.addRoad(road("R1", 5));

        assertThat(roadNetwork.containsIntersection("I1")).isTrue();
        assertThat(roadNetwork.containsIntersection("UNKNOWN")).isFalse();
        assertThat(roadNetwork.containsRoad("R1")).isTrue();
        assertThat(roadNetwork.containsRoad("UNKNOWN")).isFalse();
        assertThat(roadNetwork.getIntersectionCount()).isEqualTo(2);
        assertThat(roadNetwork.getRoadCount()).isEqualTo(1);
    }

    @Test
    void recordsVehicleMovementOnRoadAndRefreshesDensity() {
        Road road = road("R1", 5);
        roadNetwork.addRoad(road);

        Road enteredRoad = roadNetwork.recordVehicleEnteringRoad("R1");
        Road leftRoad = roadNetwork.recordVehicleLeavingRoad("R1");

        assertThat(enteredRoad).isSameAs(road);
        assertThat(leftRoad).isSameAs(road);
        assertThat(road.getCurrentVehicleCount()).isZero();
        assertThat(road.getCondition().getVehicleDensity()).isZero();
    }

    @Test
    void updatesAndQueriesRoadDensity() {
        Road lowDensityRoad = road("R1", "I1", "I2", 5);
        Road highDensityRoad = road("R2", "I2", "I1", 5);
        roadNetwork.addRoad(lowDensityRoad);
        roadNetwork.addRoad(highDensityRoad);

        lowDensityRoad.setCurrentVehicleCount(20);
        highDensityRoad.setCurrentVehicleCount(75);

        assertThat(roadNetwork.updateVehicleDensity("R1")).isEqualTo(0.2);
        assertThat(roadNetwork.getAverageVehicleDensity()).isEqualTo(0.475);
        assertThat(roadNetwork.findRoadsAtOrAboveDensity(0.5))
                .extracting(Road::getId)
                .containsExactly("R2");
    }

    @Test
    void rejectsInvalidDensityThreshold() {
        assertThatThrownBy(() -> roadNetwork.findRoadsAtOrAboveDensity(Double.NaN))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Minimum density must be finite and between 0 and 1");
        assertThatThrownBy(() -> roadNetwork.findRoadsAtOrAboveDensity(1.1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Minimum density must be finite and between 0 and 1");
    }

    @Test
    void filtersRoadsAndCountsEveryRoadStatusForVisualizationConsumers() {
        Road openRoad = road("R1", "I1", "I2", 5);
        Road congestedRoad = road("R2", "I2", "I1", 5);
        congestedRoad.setCondition(new RoadCondition(0.7, 0.6, RoadStatus.CONGESTED));
        roadNetwork.addRoad(openRoad);
        roadNetwork.addRoad(congestedRoad);

        assertThat(roadNetwork.findRoadsByStatus(RoadStatus.CONGESTED))
                .extracting(Road::getId)
                .containsExactly("R2");
        assertThat(roadNetwork.getRoadStatusCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(
                RoadStatus.OPEN, 1,
                RoadStatus.CONGESTED, 1,
                RoadStatus.BLOCKED, 0,
                RoadStatus.CLOSED, 0));
        assertThatThrownBy(() -> roadNetwork.getRoadStatusCounts().put(RoadStatus.OPEN, 99))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void calculatesAverageCongestionAndHandlesEmptyNetwork() {
        assertThat(roadNetwork.getAverageCongestionLevel()).isZero();

        Road first = road("R1", "I1", "I2", 5);
        first.setCondition(new RoadCondition(0.2, 0, RoadStatus.OPEN));
        Road second = road("R2", "I2", "I1", 5);
        second.setCondition(new RoadCondition(0.8, 0, RoadStatus.CONGESTED));
        roadNetwork.addRoad(first);
        roadNetwork.addRoad(second);

        assertThat(roadNetwork.getAverageCongestionLevel()).isEqualTo(0.5);
    }

    @Test
    void rejectsMissingStatusFilter() {
        assertThatThrownBy(() -> roadNetwork.findRoadsByStatus(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Road status is required");
    }

    @Test
    void returnsEmptyQueriesForUnknownIntersection() {
        assertThat(roadNetwork.findOutgoingRoads("UNKNOWN")).isEmpty();
        assertThat(roadNetwork.findIncomingRoads("UNKNOWN")).isEmpty();
        assertThat(roadNetwork.findOutgoingNeighbors("UNKNOWN")).isEmpty();
    }

    private Intersection intersection(String id, String name) {
        return new Intersection(id, name, 0, 0);
    }

    private Road road(String id, double distance) {
        return road(id, "I1", "I2", distance);
    }

    private Road road(String id, String startIntersectionId, String endIntersectionId) {
        return road(id, startIntersectionId, endIntersectionId, 5);
    }

    private Road road(String id, String startIntersectionId, String endIntersectionId, double distance) {
        return new Road(
                id,
                startIntersectionId,
                endIntersectionId,
                distance,
                50,
                100,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
