package team.traffic.api.dto;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.demand.CompletionPolicy;
import team.traffic.domain.demand.PlaceKind;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.demand.TripPlan;
import team.traffic.domain.demand.TripScheduleEstimator;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.simulation.DemandRunStatistics;
import team.traffic.simulation.SimulationClock;

/**
 * Reader-facing projection of the deterministic demand plan.
 *
 * <p>Identifiers remain in the contract for traceability, while display names
 * let the UI present actual places. Estimated times are clearly separated from
 * runtime observations: congestion, signals and incidents may move them.</p>
 */
public record DemandScheduleDto(
        double startTimeOfDaySeconds,
        int totalPlans,
        int failedPlans,
        List<String> failedPlanIds,
        String estimateBasis,
        List<DemandPlanDto> plans) {

    public DemandScheduleDto {
        failedPlanIds = List.copyOf(failedPlanIds);
        plans = List.copyOf(plans);
    }

    public static DemandScheduleDto of(
            List<TripPlan> plans,
            DemandRunStatistics statistics,
            RoadNetwork network,
            double startTimeOfDaySeconds) {
        List<TripPlan> safePlans = plans == null ? List.of() : List.copyOf(plans);
        DemandRunStatistics safeStatistics =
                statistics == null ? DemandRunStatistics.empty() : statistics;
        TripScheduleEstimator estimator = new TripScheduleEstimator(network);
        return new DemandScheduleDto(
                startTimeOfDaySeconds,
                safePlans.size(),
                safeStatistics.failedTrips(),
                safeStatistics.failedPlanIds(),
                TripScheduleEstimator.BASIS,
                safePlans.stream()
                        .map(plan -> plan(plan, estimator, network, startTimeOfDaySeconds))
                        .toList());
    }

    private static DemandPlanDto plan(
            TripPlan plan,
            TripScheduleEstimator estimator,
            RoadNetwork network,
            double startTimeOfDaySeconds) {
        TripScheduleEstimator.Estimate estimate = estimator.estimate(plan);
        List<DemandLegDto> legs = new ArrayList<>();
        for (int index = 0; index < plan.legs().size(); index++) {
            var leg = plan.legs().get(index);
            var legEstimate = estimate.legs().get(index);
            legs.add(new DemandLegDto(
                    legEstimate.sequence(),
                    place(leg.to(), network),
                    legEstimate.travelSeconds(),
                    leg.dwellSeconds(),
                    time(startTimeOfDaySeconds, legEstimate.arrivalSeconds()),
                    time(startTimeOfDaySeconds, legEstimate.nextDepartureSeconds()),
                    index == plan.legs().size() - 1
                            && leg.to().equals(plan.origin())));
        }
        TimeDto finalArrival = time(startTimeOfDaySeconds, estimate.finalArrivalSeconds());
        return new DemandPlanDto(
                plan.id(),
                plan.purpose().value(),
                plan.vehicleType().name(),
                place(plan.origin(), network),
                plan.completion().name(),
                time(startTimeOfDaySeconds, plan.departAtSeconds()),
                finalArrival,
                plan.completion() == CompletionPolicy.RETURN_TO_ORIGIN
                        ? finalArrival
                        : null,
                legs);
    }

    private static PlaceDto place(ResolvedPlace place, RoadNetwork network) {
        String name;
        Double latitude = null;
        Double longitude = null;
        if (place.kind() == PlaceKind.BUILDING) {
            Building building = network.getBuilding(place.id());
            if (building == null) {
                name = "Unknown building";
            } else if (building.getName() == null
                    || building.getName().matches("(?i)Building\\s+\\d+")) {
                String readableType = building.getType().name().toLowerCase().replace('_', ' ');
                name = Character.toUpperCase(readableType.charAt(0))
                        + readableType.substring(1) + " building";
            } else {
                name = building.getName();
            }
            if (building != null) {
                latitude = building.getLatitude();
                longitude = building.getLongitude();
            }
        } else {
            Intersection intersection = network.getIntersection(place.accessIntersectionId());
            String intersectionName = intersection == null
                    ? "Unknown junction"
                    : intersection.getName();
            name = place.kind() == PlaceKind.BOUNDARY
                    ? "District boundary — " + intersectionName
                    : intersectionName;
            if (intersection != null) {
                latitude = intersection.getLatitude();
                longitude = intersection.getLongitude();
            }
        }
        Intersection access = network.getIntersection(place.accessIntersectionId());
        return new PlaceDto(
                place.kind().name(),
                place.id(),
                name,
                latitude,
                longitude,
                place.accessIntersectionId(),
                access == null ? "Unknown junction" : access.getName());
    }

    private static TimeDto time(double startTimeOfDaySeconds, double elapsedSeconds) {
        double absoluteSeconds = startTimeOfDaySeconds + elapsedSeconds;
        int dayOffset = (int) Math.floor(absoluteSeconds / SimulationClock.SECONDS_PER_DAY);
        double clockSeconds = SimulationClock.timeOfDayAt(
                startTimeOfDaySeconds, elapsedSeconds);
        String clock = SimulationClock.formatTimeOfDay(clockSeconds);
        return new TimeDto(elapsedSeconds, clockSeconds, dayOffset, clock);
    }

    public record DemandPlanDto(
            String id,
            String purpose,
            String vehicleType,
            PlaceDto origin,
            String completion,
            TimeDto departure,
            TimeDto estimatedFinalArrival,
            TimeDto estimatedReturnArrival,
            List<DemandLegDto> legs) {
        public DemandPlanDto {
            legs = List.copyOf(legs);
        }
    }

    public record DemandLegDto(
            int sequence,
            PlaceDto destination,
            double estimatedTravelSeconds,
            double dwellSeconds,
            TimeDto estimatedArrival,
            TimeDto estimatedNextDeparture,
            boolean returnsToOrigin) {
    }

    public record PlaceDto(
            String kind,
            String id,
            String name,
            Double latitude,
            Double longitude,
            String accessIntersectionId,
            String accessName) {
    }

    public record TimeDto(
            double elapsedSeconds,
            double timeOfDaySeconds,
            int dayOffset,
            String clock) {
    }
}
