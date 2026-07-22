package team.traffic.simulation.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class FundamentalDiagramTest {
    @Test
    void preservesTheCapacityDropVisibleInMeasuredSamples() {
        List<RoadFlowSample> samples = List.of(
                sample(5, 12, 216),
                sample(15, 11, 594),
                sample(25, 9, 810),
                sample(40, 4, 576),
                sample(60, 1, 216));

        List<FundamentalDiagramPoint> points = new FundamentalDiagram(samples).points();

        assertThat(points).hasSize(5);
        assertThat(points.get(2).flowVehPerHourPerLane()).isGreaterThan(points.get(1).flowVehPerHourPerLane());
        assertThat(points.get(3).flowVehPerHourPerLane()).isLessThan(points.get(2).flowVehPerHourPerLane());
        assertThat(points.get(4).meanSpeedMps()).isLessThan(points.get(3).meanSpeedMps());
    }

    private static RoadFlowSample sample(double density, double speed, double flow) {
        return new RoadFlowSample("R", (long) density, density, (int) density,
                speed, density, flow);
    }
}
