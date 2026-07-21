package team.traffic.experiment;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ExperimentRunner {
    private ExperimentRunner() {}

    public static void main(String[] args) throws Exception {
        ExperimentService.ExperimentReport report = new ExperimentService().run();
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report);
        if (args.length > 0 && "--write".equals(args[0])) {
            Files.writeString(Path.of("data/experiment_results.json"), json + System.lineSeparator());
            Files.writeString(Path.of("docs/EXPERIMENT_RESULTS.md"), toMarkdown(report));
        }
        System.out.println(json);
    }

    private static String toMarkdown(ExperimentService.ExperimentReport report) {
        StringBuilder markdown = new StringBuilder("# Experiment Results\n\n")
                .append("Seed: `").append(report.seed()).append("`; route requests per scenario: `")
                .append(report.requestsPerScenario()).append("`.\n\n")
                .append("| Scenario | Algorithm | Objective | Success | Avg ms | Avg visited | Optimality |\n")
                .append("|---|---|---|---:|---:|---:|---:|\n");
        for (var scenario : report.scenarios()) {
            for (var algorithm : scenario.algorithms()) {
                markdown.append('|').append(scenario.name()).append('|').append(algorithm.algorithm())
                        .append('|').append(algorithm.objective()).append('|')
                        .append(String.format("%.0f%%", algorithm.successRate())).append('|')
                        .append(String.format("%.4f", algorithm.averageComputationTimeMillis())).append('|')
                        .append(String.format("%.1f", algorithm.averageVisitedNodes())).append('|')
                        .append(String.format("%.1f%%", algorithm.averagePathOptimality())).append("|\n");
            }
        }
        return markdown.toString();
    }
}
