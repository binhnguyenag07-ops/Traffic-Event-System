package team.traffic.simulation.history;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.simulation.SimulationStatus;

class SimulationFrameHistoryTest {

    @Test
    void retainsOnlyTheConfiguredWindowAndStartsANewTimelineOnReset() {
        RoadNetwork network = network();
        SimulationFrameHistory history = new SimulationFrameHistory(2);
        history.reset(0, 0, SimulationStatus.READY, network, List.of(), List.of(), List.of());
        String firstTimelineId = history.timeline().timelineId();

        history.record(1, 1, SimulationStatus.RUNNING, network, List.of(), List.of(), List.of());
        history.record(2, 2, SimulationStatus.PAUSED, network, List.of(), List.of(), List.of());

        assertThat(history.timeline().recordedSeconds()).containsExactly(1.0, 2.0);
        assertThat(history.frameAtOrBefore(0.9)).isEmpty();
        assertThat(history.frameAtOrBefore(1.9)).hasValueSatisfying(frame -> {
            assertThat(frame.elapsedSeconds()).isEqualTo(1.0);
            assertThat(frame.status()).isEqualTo(SimulationStatus.RUNNING);
        });

        history.reset(0, 0, SimulationStatus.READY, network, List.of(), List.of(), List.of());

        assertThat(history.timeline().timelineId()).isNotEqualTo(firstTimelineId);
        assertThat(history.timeline().recordedSeconds()).containsExactly(0.0);
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        network.addRoad(new Road("AB", "A", "B", 1, 50, 10, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
        return network;
    }
}
