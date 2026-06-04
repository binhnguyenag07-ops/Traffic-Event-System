package team.traffic.routing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RoutingStrategyRegistry {
    private final List<PathFindingStrategy> strategies = new ArrayList<>();

    public void register(PathFindingStrategy strategy) {
        // TODO: avoid duplicate strategy names.
        strategies.add(strategy);
    }

    public void unregister(String strategyName) {
        strategies.removeIf(strategy -> strategy.getName().equals(strategyName));
    }

    public PathFindingStrategy findByName(String strategyName) {
        return strategies.stream()
                .filter(strategy -> strategy.getName().equals(strategyName))
                .findFirst()
                .orElse(null);
    }

    public PathFindingStrategy findDefaultFor(String preference) {
        return strategies.stream()
                .filter(strategy -> strategy.supports(preference))
                .findFirst()
                .orElse(null);
    }

    public List<PathFindingStrategy> getStrategies() {
        return Collections.unmodifiableList(strategies);
    }
}
