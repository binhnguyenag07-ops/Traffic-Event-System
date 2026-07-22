package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadStatus;

class RoadLayoutTest {
    @Test
    void readsExplicitLaneCountAndWidthFromRoadData() {
        Road road = new Road("R", "Road", "P", "A", "B", 1, 50, 100, 0,
                4, 3.4, new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.PRIMARY);

        RoadLayout layout = new RoadDataRoadLayout();

        assertThat(layout.laneCountOf(road)).isEqualTo(4);
        assertThat(layout.laneWidthMetresOf(road)).isEqualTo(3.4);
        assertThat(layout.roadWidthMetresOf(road)).isEqualTo(13.6);
    }

    @Test
    void roadClassFallbackUsesHierarchyDefaults() {
        Road road = new Road("R", "A", "B", 1, 50, 100, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN), List.of(), RoadClass.PRIMARY);

        assertThat(new RoadClassRoadLayout().laneCountOf(road)).isEqualTo(3);
    }
}
