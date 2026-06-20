package team.traffic.routing;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadNetworkTest {

    // ── Intersection operations ──────────────────────────

    @Nested
    @DisplayName("Intersection operations")
    class IntersectionTests {

        @Test
        @DisplayName("add intersection successfully")
        void addIntersection() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));

            assertThat(network.getIntersections()).hasSize(1);
            assertThat(network.getIntersection("A")).isNotNull();
        }

        @Test
        @DisplayName("add null intersection throws")
        void addNullIntersection() {
            RoadNetwork network = new RoadNetwork();
            assertThatThrownBy(() -> network.addIntersection(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("duplicate intersection ID throws")
        void duplicateIntersectionThrows() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            assertThatThrownBy(() -> network.addIntersection(new Intersection("A", "A_renamed", 5, 5, LocationType.HOSPITAL)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate intersection id");
        }

        @Test
        @DisplayName("remove existing intersection")
        void removeIntersection() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.removeIntersection("A");

            assertThat(network.getIntersections()).isEmpty();
            assertThat(network.getIntersection("A")).isNull();
        }

        @Test
        @DisplayName("remove non-existing intersection is a no-op")
        void removeNonExistingIntersection() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.removeIntersection("Z");

            assertThat(network.getIntersections()).hasSize(1);
        }

        @Test
        @DisplayName("remove intersection cascades to connected roads")
        void removeIntersectionCascadesRoads() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));
            network.addRoad(new Road("BA", "B", "A", 5, 50, 100, 0, null));

            network.removeIntersection("A");

            assertThat(network.getIntersections()).hasSize(1);
            assertThat(network.getRoads()).isEmpty();
        }

        @Test
        @DisplayName("get unmodifiable intersections map")
        void unmodifiableIntersections() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));

            Map<String, Intersection> intersections = network.getIntersections();
            assertThatThrownBy(() -> intersections.put("X", new Intersection("X", "X", 0, 0, LocationType.NORMAL)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── Road operations ──────────────────────────────────

    @Nested
    @DisplayName("Road operations")
    class RoadTests {

        @Test
        @DisplayName("add road successfully")
        void addRoad() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));

            assertThat(network.getRoads()).hasSize(1);
            assertThat(network.getRoad("AB")).isNotNull();
        }

        @Test
        @DisplayName("add null road throws")
        void addNullRoad() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            assertThatThrownBy(() -> network.addRoad(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("add road with non-existing start intersection throws")
        void addRoadMissingStart() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            assertThatThrownBy(() -> network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Start intersection does not exist");
        }

        @Test
        @DisplayName("add road with non-existing end intersection throws")
        void addRoadMissingEnd() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            assertThatThrownBy(() -> network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End intersection does not exist");
        }

        @Test
        @DisplayName("remove road by ID")
        void removeRoad() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));

            network.removeRoad("AB");
            assertThat(network.getRoads()).isEmpty();
        }

        @Test
        @DisplayName("remove non-existing road is a no-op")
        void removeNonExistingRoad() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));

            network.removeRoad("ZZ");
            assertThat(network.getRoads()).hasSize(1);
        }

        @Test
        @DisplayName("duplicate road ID throws")
        void duplicateRoadThrows() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));
            assertThatThrownBy(() -> network.addRoad(new Road("AB", "A", "B", 10, 100, 200, 0, null)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Duplicate road id");
        }

        @Test
        @DisplayName("self-loop road is allowed")
        void selfLoopRoad() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addRoad(new Road("AA", "A", "A", 3, 50, 100, 0, null));

            assertThat(network.getRoads()).hasSize(1);
            assertThat(network.getRoad("AA").getStartIntersectionId()).isEqualTo("A");
            assertThat(network.getRoad("AA").getEndIntersectionId()).isEqualTo("A");
        }

        @Test
        @DisplayName("get unmodifiable roads map")
        void unmodifiableRoads() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));

            Map<String, Road> roads = network.getRoads();
            assertThatThrownBy(() -> roads.put("X", new Road("X", "A", "B", 5, 50, 100, 0, null)))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // ── Find operations ──────────────────────────────────

    @Nested
    @DisplayName("Find operations")
    class FindTests {

        @Test
        @DisplayName("find outgoing roads returns directed roads from intersection")
        void findOutgoingRoads() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("C", "C", 0, 5, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));
            network.addRoad(new Road("AC", "A", "C", 5, 50, 100, 0, null));
            network.addRoad(new Road("BA", "B", "A", 5, 50, 100, 0, null));

            List<Road> outgoing = network.findOutgoingRoads("A");
            assertThat(outgoing).hasSize(2);
            assertThat(outgoing).allMatch(r -> r.getStartIntersectionId().equals("A"));
        }

        @Test
        @DisplayName("find outgoing roads returns empty list for intersection with no roads")
        void findOutgoingRoadsEmpty() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));

            List<Road> outgoing = network.findOutgoingRoads("A");
            assertThat(outgoing).isEmpty();
        }

        @Test
        @DisplayName("find incoming roads returns directed roads to intersection")
        void findIncomingRoads() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("C", "C", 0, 5, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));
            network.addRoad(new Road("CB", "C", "B", 5, 50, 100, 0, null));

            List<Road> incoming = network.findIncomingRoads("B");
            assertThat(incoming).hasSize(2);
            assertThat(incoming).allMatch(r -> r.getEndIntersectionId().equals("B"));
        }

        @Test
        @DisplayName("find incoming roads returns empty list for intersection with no roads")
        void findIncomingRoadsEmpty() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));

            List<Road> incoming = network.findIncomingRoads("A");
            assertThat(incoming).isEmpty();
        }

        @Test
        @DisplayName("update road condition for existing road")
        void updateRoadCondition() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB", "A", "B", 5, 50, 100, 0, null));

            network.updateRoadCondition("AB", new RoadCondition(0.8, 0.5, RoadStatus.CONGESTED));

            assertThat(network.getRoad("AB").getCondition().getCongestionLevel()).isEqualTo(0.8);
            assertThat(network.getRoad("AB").getCondition().getRoadStatus()).isEqualTo(RoadStatus.CONGESTED);
        }

        @Test
        @DisplayName("update road condition for non-existing road throws")
        void updateRoadConditionNonExisting() {
            RoadNetwork network = new RoadNetwork();
            assertThatThrownBy(() -> network.updateRoadCondition("ZZ", new RoadCondition(0, 0, RoadStatus.OPEN)))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Road does not exist");
        }
    }

    // ── Edge cases ───────────────────────────────────────

    @Nested
    @DisplayName("Edge cases")
    class EdgeCaseTests {

        @Test
        @DisplayName("empty network has no intersections or roads")
        void emptyNetwork() {
            RoadNetwork network = new RoadNetwork();
            assertThat(network.getIntersections()).isEmpty();
            assertThat(network.getRoads()).isEmpty();
            assertThat(network.findOutgoingRoads("X")).isEmpty();
            assertThat(network.findIncomingRoads("X")).isEmpty();
        }

        @Test
        @DisplayName("single node, no roads")
        void singleNodeNoRoads() {
            RoadNetwork network = TestGraph.buildSingleNodeGraph();
            assertThat(network.getIntersections()).hasSize(1);
            assertThat(network.getRoads()).isEmpty();
            assertThat(network.findOutgoingRoads("A")).isEmpty();
        }

        @Test
        @DisplayName("disconnected graph: no path between components")
        void disconnectedGraph() {
            RoadNetwork network = TestGraph.buildDisconnectedGraph();
            assertThat(network.findOutgoingRoads("A")).hasSize(1);
            assertThat(network.findOutgoingRoads("C")).hasSize(1);
            // No road from A to C
            assertThat(network.findOutgoingRoads("A").stream()
                    .anyMatch(r -> r.getEndIntersectionId().equals("C"))).isFalse();
        }

        @Test
        @DisplayName("multiple roads between same intersections allowed")
        void multipleRoadsBetweenSamePair() {
            RoadNetwork network = new RoadNetwork();
            network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
            network.addIntersection(new Intersection("B", "B", 5, 0, LocationType.NORMAL));
            network.addRoad(new Road("AB_fast", "A", "B", 5, 100, 100, 0, null));
            network.addRoad(new Road("AB_slow", "A", "B", 5, 30, 100, 0, null));

            assertThat(network.getRoads()).hasSize(2);
            assertThat(network.findOutgoingRoads("A")).hasSize(2);
        }
    }
}
