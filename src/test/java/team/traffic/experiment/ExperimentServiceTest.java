package team.traffic.experiment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExperimentServiceTest {

    @Test
    void runsAllRequiredScenariosWithDeterministicStructure() {
        var report = new ExperimentService().run();

        assertThat(report.seed()).isEqualTo(42);
        assertThat(report.requestsPerScenario()).isEqualTo(30);
        assertThat(report.scenarios()).extracting(item -> item.id())
                .containsExactly("normal", "heavy-congestion", "random-events", "large-network");
        assertThat(report.scenarios()).allSatisfy(scenario -> {
            assertThat(scenario.algorithms()).extracting(item -> item.algorithm())
                    .containsExactly("BFS", "Dijkstra", "A*");
            assertThat(scenario.algorithms()).allSatisfy(algorithm -> {
                assertThat(algorithm.requests()).isEqualTo(30);
                assertThat(algorithm.averagePathOptimality()).isBetween(0.0, 100.0);
            });
        });

        var large = report.scenarios().stream().filter(item -> item.id().equals("large-network")).findFirst().orElseThrow();
        double dijkstraVisited = large.algorithms().stream().filter(item -> item.algorithm().equals("Dijkstra"))
                .findFirst().orElseThrow().averageVisitedNodes();
        double aStarVisited = large.algorithms().stream().filter(item -> item.algorithm().equals("A*"))
                .findFirst().orElseThrow().averageVisitedNodes();
        assertThat(aStarVisited).isLessThan(dijkstraVisited);
    }
}
