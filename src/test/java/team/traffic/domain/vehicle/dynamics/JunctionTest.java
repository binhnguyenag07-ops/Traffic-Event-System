package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class JunctionTest {
    @Test
    void filtersArtificialDegreeTwoNodesFromRealJunctions() {
        RoadNetwork network = network("A", "J", "B");
        addRoad(network, "AJ", "A", "J");
        addRoad(network, "JB", "J", "B");

        assertThat(Junction.at(network, "J").degree()).isEqualTo(2);
        assertThat(Junction.at(network, "J").isRealJunction()).isFalse();
    }

    @Test
    void recognizesThreePhysicalBranches() {
        RoadNetwork network = network("A", "J", "B", "C");
        addRoad(network, "AJ", "A", "J");
        addRoad(network, "JB", "J", "B");
        addRoad(network, "JC", "J", "C");

        assertThat(Junction.at(network, "J").isRealJunction()).isTrue();
        assertThat(Junction.at(network, "J").estimatedRadiusMetres()).isPositive();
    }

    private static RoadNetwork network(String... ids) {
        RoadNetwork network = new RoadNetwork();
        for (int index = 0; index < ids.length; index++) {
            network.addIntersection(new Intersection(ids[index], ids[index], index, 0, LocationType.NORMAL));
        }
        return network;
    }

    private static void addRoad(RoadNetwork network, String id, String from, String to) {
        network.addRoad(new Road(id, from, to, 1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
    }
}
