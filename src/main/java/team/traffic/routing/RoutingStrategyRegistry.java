package team.traffic.routing;

import team.traffic.domain.routing.RoutePreference;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RoutingStrategyRegistry {
    private final List<PathFindingStrategy> strategies = new ArrayList<>();

    public void register(PathFindingStrategy strategy) {
        strategies.add(strategy);
    }

    public Optional<PathFindingStrategy> findByName(String name) {
        return strategies.stream()
                .filter(s -> s.getName().equalsIgnoreCase(name))
                .findFirst();
    }

    public Optional<PathFindingStrategy> findForPreference(RoutePreference preference) {
        return strategies.stream()
                .filter(s -> s.supports(preference))
                .findFirst();
    }

    public List<PathFindingStrategy> getAll() {
        return List.copyOf(strategies);
    }
}
