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
        int fromIndex = firstDepartureAfter(fromExclusiveSeconds);
        int toIndex = firstDepartureAfter(toInclusiveSeconds);
        return plans.subList(fromIndex, toIndex);
    }

    /**
     * Binary upper-bound lookup keeps each scheduler poll logarithmic in the
     * full-day plan count. A physical-view tick must not rescan tens of
     * thousands of future departures merely to discover the handful due now.
     */
    private int firstDepartureAfter(double seconds) {
        int low = 0;
        int high = plans.size();
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (plans.get(middle).departAtSeconds() <= seconds) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    @Override
    public int scheduledTripCount() {
        return plans.size() + generationFailedPlanIds.size();
    }

    @Override
    public List<String> generationFailedPlanIds() {
        return generationFailedPlanIds;
    }

    @Override
    public List<TripPlan> allPlans() {
        return plans;
    }

    private GenerationResult generate(RoadNetwork network, DemandScenario scenario) {
        PlaceResolver resolver = new PlaceResolver(network, scenario.zones());
        ReachabilityIndex reachability = new ReachabilityIndex(network);
        Random random = new Random(scenario.seed());
        List<TripPlan> generated = new ArrayList<>();
        List<String> failedPlanIds = new ArrayList<>();
        Map<CandidateKey, List<List<ResolvedPlace>>> candidatesByFlowAndOrigin =
                new HashMap<>();
        Map<DestinationChoiceKey, WeightedDestinations> destinationChoices =
                new HashMap<>();
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
                            failedPlanIds, candidatesByFlowAndOrigin,
                            destinationChoices, source, origin, dailyProduction);
                }
            } else {
                for (FlowSpec flow : source.flows()) {
                    List<ResolvedPlace> viableOrigins = origins.stream()
                            .filter(origin -> hasViablePlan(
                                     resolver,
                                     reachability,
                                     candidatesByFlowAndOrigin,
                                     source.id(),
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
                            failedPlanIds, candidatesByFlowAndOrigin,
                            destinationChoices, source.id(), flow,
                            source.production().vehiclesPerDay(),
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
            Map<CandidateKey, List<List<ResolvedPlace>>> candidatesByFlowAndOrigin,
            Map<DestinationChoiceKey, WeightedDestinations> destinationChoices,
            DemandSource source,
            ResolvedPlace origin,
            double dailyProduction) {
        for (FlowSpec flow : source.flows()) {
            if (!hasViablePlan(
                    resolver,
                    reachability,
                    candidatesByFlowAndOrigin,
                    source.id(),
                    flow,
                    origin)) {
                continue;
            }
            generateFlow(network, scenario, resolver, reachability,
                    random, generated, sequence,
                    failedPlanIds, candidatesByFlowAndOrigin,
                    destinationChoices, source.id(), flow,
                    dailyProduction, () -> origin);
        }
    }

    private boolean hasViablePlan(
            PlaceResolver resolver,
            ReachabilityIndex reachability,
            Map<CandidateKey, List<List<ResolvedPlace>>> candidatesByFlowAndOrigin,
            String sourceId,
            FlowSpec flow,
            ResolvedPlace origin) {
        List<List<ResolvedPlace>> candidatesByLeg = candidatesFor(
                resolver, candidatesByFlowAndOrigin, sourceId, flow, origin);
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
            Map<CandidateKey, List<List<ResolvedPlace>>> candidatesByFlowAndOrigin,
            Map<DestinationChoiceKey, WeightedDestinations> destinationChoices,
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
                        candidatesByFlowAndOrigin, destinationChoices,
                        sourceId, flow, vehicleType, origin, departure);
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
            Map<CandidateKey, List<List<ResolvedPlace>>> candidatesByFlowAndOrigin,
            Map<DestinationChoiceKey, WeightedDestinations> destinationChoices,
            String sourceId,
            FlowSpec flow,
            VehicleType vehicleType,
            ResolvedPlace origin,
            double departure) {
        List<List<ResolvedPlace>> candidatesByLeg = candidatesFor(
                resolver, candidatesByFlowAndOrigin, sourceId, flow, origin);
        List<TripLeg> legs = new ArrayList<>();
        ResolvedPlace current = origin;
        for (int legIndex = 0; legIndex < flow.legs().size(); legIndex++) {
            int currentLegIndex = legIndex;
            LegSpec legSpec = flow.legs().get(currentLegIndex);
            ResolvedPlace legOrigin = current;
            int suffixStartIndex = currentLegIndex + 1;
            DestinationChoiceKey key = new DestinationChoiceKey(
                    sourceId, flow.id(), currentLegIndex, origin, legOrigin);
            WeightedDestinations choices = destinationChoices.computeIfAbsent(
                    key,
                    ignored -> {
                        Map<ViabilityKey, Boolean> suffixViability = new HashMap<>();
                        List<ResolvedPlace> candidates = candidatesByLeg.get(currentLegIndex).stream()
                                .filter(candidate -> reachability.canReach(
                                        legOrigin, candidate))
                                .filter(candidate -> canCompleteSuffix(
                                        candidate,
                                        suffixStartIndex,
                                        origin,
                                        flow,
                                        candidatesByLeg,
                                        reachability,
                                        suffixViability))
                                .toList();
                        return weightedDestinations(
                                network,
                                scenario.parameters(),
                                legOrigin,
                                candidates);
                    });
            if (choices.isEmpty()) {
                return Optional.empty();
            }
            ResolvedPlace destination = choices.choose(random);
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

    private List<List<ResolvedPlace>> candidatesFor(
            PlaceResolver resolver,
            Map<CandidateKey, List<List<ResolvedPlace>>> candidatesByFlowAndOrigin,
            String sourceId,
            FlowSpec flow,
            ResolvedPlace origin) {
        return candidatesByFlowAndOrigin.computeIfAbsent(
                new CandidateKey(sourceId, flow.id(), origin),
                ignored -> flow.legs().stream()
                        .map(legSpec -> resolver.resolve(legSpec.to(), origin))
                        .toList());
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

    private WeightedDestinations weightedDestinations(
            RoadNetwork network,
            DemandParameters parameters,
            ResolvedPlace current,
            List<ResolvedPlace> candidates) {
        if (candidates.isEmpty()) return WeightedDestinations.empty();
        List<ResolvedPlace> usable = candidates.size() <= 1
                ? candidates
                : candidates.stream().filter(candidate -> !candidate.equals(current)).toList();
        if (usable.isEmpty()) usable = candidates;

        double[] cumulativeWeights = new double[usable.size()];
        double total = 0;
        for (int index = 0; index < usable.size(); index++) {
            ResolvedPlace candidate = usable.get(index);
            double attraction = attractionOf(network, parameters, candidate);
            double distanceKm = distanceKm(network, current, candidate);
            double weight = attraction * Math.exp(-parameters.distanceDecayBeta() * distanceKm);
            total += Double.isFinite(weight) && weight > 0 ? weight : 0;
            cumulativeWeights[index] = total;
        }
        if (total <= 0) {
            throw new IllegalArgumentException(
                    "Destination candidates have no positive attraction weight");
        }
        return new WeightedDestinations(usable, cumulativeWeights, total);
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

    private record CandidateKey(
            String sourceId,
            String flowId,
            ResolvedPlace origin) {}

    private record DestinationChoiceKey(
            String sourceId,
            String flowId,
            int legIndex,
            ResolvedPlace origin,
            ResolvedPlace current) {}

    private record WeightedDestinations(
            List<ResolvedPlace> places,
            double[] cumulativeWeights,
            double totalWeight) {

        private WeightedDestinations {
            places = List.copyOf(places);
            cumulativeWeights = cumulativeWeights.clone();
        }

        private static WeightedDestinations empty() {
            return new WeightedDestinations(List.of(), new double[0], 0);
        }

        private boolean isEmpty() {
            return places.isEmpty();
        }

        private ResolvedPlace choose(RandomGenerator random) {
            double draw = random.nextDouble() * totalWeight;
            int low = 0;
            int high = cumulativeWeights.length - 1;
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (draw <= cumulativeWeights[middle]) {
                    high = middle;
                } else {
                    low = middle + 1;
                }
            }
            return places.get(low);
        }
    }

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
            // Vehicle demand must traverse at least one directed road. Two
            // different buildings frequently share the same generated access
            // junction; accepting that zero-road pair spawns an already-arrived
            // vehicle that never appears in the simulation.
            if (start.equals(destination)) {
                return false;
            }
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
