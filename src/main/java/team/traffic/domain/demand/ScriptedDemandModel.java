package team.traffic.domain.demand;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ScriptedDemandModel implements TripDemandModel {
    private final List<TripPlan> plans;

    public ScriptedDemandModel(List<TripPlan> plans) {
        this.plans = plans == null ? List.of() : plans.stream()
                .sorted(Comparator.comparingDouble(TripPlan::departAtSeconds).thenComparing(TripPlan::id))
                .toList();
        Set<String> ids = new HashSet<>();
        for (TripPlan plan : this.plans) {
            if (!ids.add(plan.id())) {
                throw new IllegalArgumentException("Duplicate scripted trip plan id: " + plan.id());
            }
        }
    }

    @Override
    public List<TripPlan> tripsBeginningIn(double fromExclusiveSeconds, double toInclusiveSeconds) {
        validateInterval(fromExclusiveSeconds, toInclusiveSeconds);
        return plans.stream()
                .filter(plan -> plan.departAtSeconds() > fromExclusiveSeconds
                        && plan.departAtSeconds() <= toInclusiveSeconds)
                .toList();
    }

    @Override
    public int scheduledTripCount() {
        return plans.size();
    }

    @Override
    public List<TripPlan> allPlans() {
        return plans;
    }

    private static void validateInterval(double from, double to) {
        if (!Double.isFinite(from) || !Double.isFinite(to) || to < from) {
            throw new IllegalArgumentException("Demand interval must be finite and ordered");
        }
    }
}
