package team.traffic.domain.demand;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.simulation.SimulationClock;

/**
 * Seeded building/place demand generator.
 *
 * <p>It resolves the whole schedule once. Querying intervals later is therefore
 * deterministic and side-effect free.</p>
 */
public final class GravityDemandModel implements TripDemandModel {
    private static final double EARTH_RADIUS_METRES = 6_371_000.0;

    private final List<TripPlan> plans;
    private final List<String> generationFailedPlanIds;

    public GravityDemandModel(RoadNetwork network, DemandScenario scenario) {
        if (network == null) throw new IllegalArgumentException("Road network is required");
        if (scenario == null) throw new IllegalArgumentException("Demand scenario is required");
        GenerationResult result = generate(network, scenario);
        this.plans = result.plans();
        this.generationFailedPlanIds = result.failedPlanIds();
    }

    @Override
    public List<TripPlan> tripsBeginningIn(double fromExclusiveSeconds, double toInclusiveSeconds) {
        if (!Double.isFinite(fromExclusiveSeconds) || !Double.isFinite(toInclusiveSeconds)
                || toInclusiveSeconds < fromExclusiveSeconds) {
            throw new IllegalArgumentException("Demand interval must be finite and ordered");
        }
        return plans.stream()
                .filter(plan -> plan.departAtSeconds() > fromExclusiveSeconds
                        && plan.departAtSeconds() <= toInclusiveSeconds)
                .toList();
    }

    @Override
    public int scheduledTripCount() {
        return plans.size() + generationFailedPlanIds.size();
    }

    @Override
    public List<String> generationFailedPlanIds() {
        return generationFailedPlanIds;
    }

    public List<TripPlan> allPlans() {
        return plans;
    }

    private GenerationResult generate(RoadNetwork network, DemandScenario scenario) {
        PlaceResolver resolver = new PlaceResolver(network, scenario.zones());
        ReachabilityIndex reachability = new ReachabilityIndex(network);
        Random random = new Random(scenario.seed());
        List<TripPlan> generated = new ArrayList<>();
        List<String> failedPlanIds = new ArrayList<>();
        int[] sequence = {0};

        for (DemandSource source : scenario.sources()) {
            List<ResolvedPlace> origins = resolver.resolve(source.from(), null);
            if (source.production().kind() == ProductionSpec.Kind.POPULATION_DERIVED) {
                List<ResolvedPlace> buildingOrigins = origins.stream()
                        .filter(place -> place.kind() == PlaceKind.BUILDING)
                        .toList();
                if (buildingOrigins.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Population-derived source resolves no building: " + source.id());
                }
                for (ResolvedPlace origin : buildingOrigins) {
                    Building building = network.getBuilding(origin.id());
                    double dailyProduction = occupancy(building, scenario.parameters())
                            * source.production().tripsPerPersonPerDay();
                    generateForOrigin(network, scenario, resolver, reachability,
                            random, generated, sequence,
                            failedPlanIds, source, origin, dailyProduction);
                }
            } else {
                for (FlowSpec flow : source.flows()) {
                    List<ResolvedPlace> viableOrigins = origins.stream()
                            .filter(origin -> hasViablePlan(
                                    resolver,
                                    reachability,
                                    flow,
                                    origin))
                            .toList();
                    if (viableOrigins.isEmpty()) {
                        throw new IllegalArgumentException(
                                "Demand source " + source.id() + " flow " + flow.id()
                                        + " has no structurally reachable origin-to-destination chain");
                    }
                    generateFlow(network, scenario, resolver, reachability,
                            random, generated, sequence,
                            failedPlanIds, source.id(), flow, source.production().vehiclesPerDay(),
                            () -> viableOrigins.get(random.nextInt(viableOrigins.size())));
                }
            }
        }

        return new GenerationResult(
                generated.stream()
                        .sorted(Comparator.comparingDouble(TripPlan::departAtSeconds)
                                .thenComparing(TripPlan::id))
                        .toList(),
                List.copyOf(failedPlanIds));
    }

    private void generateForOrigin(
            RoadNetwork network,
            DemandScenario scenario,
            PlaceResolver resolver,
            ReachabilityIndex reachability,
            Random random,
            List<TripPlan> generated,
            int[] sequence,
            List<String> failedPlanIds,
            DemandSource source,
            ResolvedPlace origin,
            double dailyProduction) {
        for (FlowSpec flow : source.flows()) {
            if (!hasViablePlan(resolver, reachability, flow, origin)) {
                continue;
            }
            generateFlow(network, scenario, resolver, reachability,
                    random, generated, sequence,
                    failedPlanIds, source.id(), flow, dailyProduction, () -> origin);
        }
    }

    private boolean hasViablePlan(
            PlaceResolver resolver,
            ReachabilityIndex reachability,
            FlowSpec flow,
            ResolvedPlace origin) {
        List<List<ResolvedPlace>> candidatesByLeg = flow.legs().stream()
                .map(legSpec -> resolver.resolve(legSpec.to(), origin))
                .toList();
        return canCompleteSuffix(
                origin,
                0,
                origin,
                flow,
                candidatesByLeg,
                reachability,
                new HashMap<>());
    }

    private void generateFlow(
            RoadNetwork network,
            DemandScenario scenario,
            PlaceResolver resolver,
            ReachabilityIndex reachability,
            Random random,
            List<TripPlan> generated,
            int[] sequence,
            List<String> failedPlanIds,
            String sourceId,
            FlowSpec flow,
            double sourceDailyProduction,
            java.util.function.Supplier<ResolvedPlace> originSupplier) {
        double cursor = 0;
        while (cursor < scenario.durationSeconds()) {
            double timeOfDay = SimulationClock.timeOfDayAt(scenario.startTimeOfDaySeconds(), cursor);
            int hour = (int) (timeOfDay / 3_600);
            double secondsUntilHourEnd = 3_600 - (timeOfDay % 3_600);
            double intervalSeconds = Math.min(secondsUntilHourEnd, scenario.durationSeconds() - cursor);
            double expected = sourceDailyProduction
                    * flow.share()
                    * flow.curve().weightAtHour(hour)
                    * (intervalSeconds / 3_600.0)
                    * scenario.scale();
            int count = stochasticRound(expected, random);
            for (int index = 0; index < count; index++) {
                ResolvedPlace origin = originSupplier.get();
                double departure = cursor + random.nextDouble() * intervalSeconds;
                VehicleType vehicleType = chooseVehicleType(flow.vehicleMix(), random);
                String planId = sourceId + "-" + flow.id() + "-" + String.format("%07d", sequence[0]++);
                Optional<TripPlan> plan = resolvePlan(
                        network, scenario, resolver, reachability, random, planId,
                        flow, vehicleType, origin, departure);
                if (plan.isPresent()
                        && TripPlanReachability.isReachable(network, plan.orElseThrow())) {
                    generated.add(plan.orElseThrow());
                } else {
                    failedPlanIds.add(planId);
                }
            }
            cursor += intervalSeconds;
        }
    }

    private Optional<TripPlan> resolvePlan(
            RoadNetwork network,
            DemandScenario scenario,
            PlaceResolver resolver,
            ReachabilityIndex reachability,
            RandomGenerator random,
            String planId,
            FlowSpec flow,
            VehicleType vehicleType,
            ResolvedPlace origin,
            double departure) {
        List<List<ResolvedPlace>> candidatesByLeg = flow.legs().stream()
                .map(legSpec -> resolver.resolve(legSpec.to(), origin))
                .toList();
        Map<ViabilityKey, Boolean> suffixViability = new HashMap<>();
        List<TripLeg> legs = new ArrayList<>();
        ResolvedPlace current = origin;
        for (int legIndex = 0; legIndex < flow.legs().size(); legIndex++) {
            LegSpec legSpec = flow.legs().get(legIndex);
            ResolvedPlace legOrigin = current;
            int suffixStartIndex = legIndex + 1;
            List<ResolvedPlace> candidates = candidatesByLeg.get(legIndex).stream()
                    .filter(candidate -> reachability.canReach(legOrigin, candidate))
                    .filter(candidate -> canCompleteSuffix(
                            candidate,
                            suffixStartIndex,
                            origin,
                            flow,
                            candidatesByLeg,
                            reachability,
                            suffixViability))
                    .toList();
            if (candidates.isEmpty()) {
                return Optional.empty();
            }
            ResolvedPlace destination = chooseDestination(
                    network, scenario.parameters(), current, candidates, random);
            legs.add(new TripLeg(destination, legSpec.dwell().resolve(random)));
            current = destination;
        }
        if (flow.completion() == CompletionPolicy.RETURN_TO_ORIGIN && !current.equals(origin)) {
            legs.add(new TripLeg(origin, 0));
        }
        return Optional.of(new TripPlan(
                planId,
                flow.purpose(),
                vehicleType,
                origin,
                legs,
                flow.completion(),
                departure));
    }

    private boolean canCompleteSuffix(
            ResolvedPlace current,
            int nextLegIndex,
            ResolvedPlace origin,
            FlowSpec flow,
            List<List<ResolvedPlace>> candidatesByLeg,
            ReachabilityIndex reachability,
            Map<ViabilityKey, Boolean> memo) {
        ViabilityKey key = new ViabilityKey(
                current.accessIntersectionId(),
                nextLegIndex);
        Boolean cached = memo.get(key);
        if (cached != null) return cached;

        boolean viable;
        if (nextLegIndex == candidatesByLeg.size()) {
            viable = flow.completion() != CompletionPolicy.RETURN_TO_ORIGIN
                    || reachability.canReach(current, origin);
        } else {
            viable = candidatesByLeg.get(nextLegIndex).stream()
                    .anyMatch(candidate -> reachability.canReach(current, candidate)
                            && canCompleteSuffix(
                                    candidate,
                                    nextLegIndex + 1,
                                    origin,
                                    flow,
                                    candidatesByLeg,
                                    reachability,
                                    memo));
        }
        memo.put(key, viable);
        return viable;
    }

    private ResolvedPlace chooseDestination(
            RoadNetwork network,
            DemandParameters parameters,
            ResolvedPlace current,
            List<ResolvedPlace> candidates,
            RandomGenerator random) {
        List<ResolvedPlace> usable = candidates.size() <= 1
                ? candidates
                : candidates.stream().filter(candidate -> !candidate.equals(current)).toList();
        if (usable.isEmpty()) usable = candidates;

        double[] weights = new double[usable.size()];
        double total = 0;
        for (int index = 0; index < usable.size(); index++) {
            ResolvedPlace candidate = usable.get(index);
            double attraction = attractionOf(network, parameters, candidate);
            double distanceKm = distanceKm(network, current, candidate);
            double weight = attraction * Math.exp(-parameters.distanceDecayBeta() * distanceKm);
            weights[index] = Double.isFinite(weight) && weight > 0 ? weight : 0;
            total += weights[index];
        }
        if (total <= 0) {
            throw new IllegalArgumentException(
                    "Destination candidates have no positive attraction weight");
        }
        double draw = random.nextDouble() * total;
        for (int index = 0; index < usable.size(); index++) {
            draw -= weights[index];
            if (draw <= 0) return usable.get(index);
        }
        return usable.get(usable.size() - 1);
    }

    private double attractionOf(
            RoadNetwork network,
            DemandParameters parameters,
            ResolvedPlace place) {
        if (place.kind() != PlaceKind.BUILDING) return 1.0;
        Building building = network.getBuilding(place.id());
        BuildingDemandParameters type = parameters.forType(building.getType());
        return type.attractionWeight() * Math.max(1.0, footprintAreaSquareMetres(building));
    }

    private double occupancy(Building building, DemandParameters parameters) {
        BuildingDemandParameters type = parameters.forType(building.getType());
        double area = footprintAreaSquareMetres(building);
        return area > 0
                ? area * type.occupantsPerSquareMetre()
                : type.fallbackOccupants();
    }

    static double footprintAreaSquareMetres(Building building) {
        List<GeoPoint> points = building.getFootprint();
        if (points.size() < 3) return 0;
        double referenceLatitude = points.stream().mapToDouble(GeoPoint::latitude).average().orElse(0);
        double cosine = Math.cos(Math.toRadians(referenceLatitude));
        double twiceArea = 0;
        for (int index = 0; index < points.size(); index++) {
            GeoPoint first = points.get(index);
            GeoPoint second = points.get((index + 1) % points.size());
            double firstX = Math.toRadians(first.longitude()) * EARTH_RADIUS_METRES * cosine;
            double firstY = Math.toRadians(first.latitude()) * EARTH_RADIUS_METRES;
            double secondX = Math.toRadians(second.longitude()) * EARTH_RADIUS_METRES * cosine;
            double secondY = Math.toRadians(second.latitude()) * EARTH_RADIUS_METRES;
            twiceArea += firstX * secondY - secondX * firstY;
        }
        return Math.abs(twiceArea) / 2.0;
    }

    private double distanceKm(RoadNetwork network, ResolvedPlace first, ResolvedPlace second) {
        Intersection a = network.getIntersection(first.accessIntersectionId());
        Intersection b = network.getIntersection(second.accessIntersectionId());
        if (a == null || b == null) return 0;
        if (a.getLatitude() != null && b.getLatitude() != null) {
            double lat1 = Math.toRadians(a.getLatitude());
            double lat2 = Math.toRadians(b.getLatitude());
            double deltaLat = lat2 - lat1;
            double deltaLon = Math.toRadians(b.getLongitude() - a.getLongitude());
            double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                    + Math.cos(lat1) * Math.cos(lat2)
                    * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
            return 2 * EARTH_RADIUS_METRES * Math.asin(Math.sqrt(Math.min(1, h))) / 1_000.0;
        }
        return Math.hypot(a.getX() - b.getX(), a.getY() - b.getY());
    }

    private int stochasticRound(double expected, RandomGenerator random) {
        int whole = (int) Math.floor(expected);
        return whole + (random.nextDouble() < expected - whole ? 1 : 0);
    }

    private VehicleType chooseVehicleType(List<VehicleMixEntry> mix, RandomGenerator random) {
        double draw = random.nextDouble();
        for (VehicleMixEntry entry : mix) {
            draw -= entry.share();
            if (draw <= 0) return entry.type();
        }
        return mix.get(mix.size() - 1).type();
    }

    private record ViabilityKey(String accessIntersectionId, int nextLegIndex) {}

    /** Directed-topology reachability cache shared by every plan in one generation run. */
    private static final class ReachabilityIndex {
        private final RoadNetwork network;
        private final Map<String, Set<String>> reachableByOrigin = new HashMap<>();

        private ReachabilityIndex(RoadNetwork network) {
            this.network = network;
        }

        private boolean canReach(ResolvedPlace from, ResolvedPlace to) {
            String start = from.accessIntersectionId();
            String destination = to.accessIntersectionId();
            if (!network.containsIntersection(start)
                    || !network.containsIntersection(destination)) {
                return false;
            }
            return reachableByOrigin
                    .computeIfAbsent(start, this::findReachableIntersections)
                    .contains(destination);
        }

        private Set<String> findReachableIntersections(String start) {
            ArrayDeque<String> frontier = new ArrayDeque<>();
            Set<String> visited = new HashSet<>();
            frontier.add(start);
            visited.add(start);
            while (!frontier.isEmpty()) {
                String current = frontier.removeFirst();
                for (var road : network.findOutgoingRoads(current)) {
                    String next = road.getEndIntersectionId();
                    if (visited.add(next)) frontier.addLast(next);
                }
            }
            return Set.copyOf(visited);
        }
    }

    private record GenerationResult(List<TripPlan> plans, List<String> failedPlanIds) {}
}
