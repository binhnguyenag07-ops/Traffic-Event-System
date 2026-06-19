package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private Intersection intersection(String id, String name) {
        return new Intersection(id, name, 0, 0, LocationType.NORMAL);
    }

    private Road road(String id, double distance) {
        return new Road(
                id,
                "I1",
                "I2",
                distance,
                50,
                100,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
