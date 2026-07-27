package team.traffic.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.JunctionMovement;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.MovementPermission;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;
import team.traffic.domain.vehicle.pose.PolylineGeometry;

/**
 * Converts one explicit mid-block turnaround into an ordinary synthetic
 * junction and four directed road segments. No vehicle/runtime special case is
 * needed after this import-time normalization.
 */
final class TurnaroundPointNormalizer {
    private static final int GEOMETRY_SAMPLES_PER_SEGMENT = 5;

    void normalize(
            RoadNetwork network,
            String id,
            String physicalRoadId,
            String referenceRoadId,
            double fractionAlongReferenceRoad,
            Set<VehicleType> allowedVehicleTypes,
            double penaltySeconds) {
        if (network == null || id == null || id.isBlank()
                || physicalRoadId == null || physicalRoadId.isBlank()
                || referenceRoadId == null || referenceRoadId.isBlank()
                || !Double.isFinite(fractionAlongReferenceRoad)
                || fractionAlongReferenceRoad <= 0
                || fractionAlongReferenceRoad >= 1
                || !Double.isFinite(penaltySeconds)
                || penaltySeconds < 0) {
            throw new IllegalArgumentException("Turnaround point is invalid.");
        }
        if (network.containsIntersection(id)) {
            throw new IllegalArgumentException(
                    "Turnaround id already exists as an intersection: " + id);
        }
        Road reference = network.getRoad(referenceRoadId);
        if (reference == null
                || !physicalRoadId.equals(reference.getPhysicalRoadId())) {
            throw new IllegalArgumentException(
                    "Reference road does not belong to physical road " + physicalRoadId);
        }
        Road reverse = network.findRoadsByPhysicalRoadId(physicalRoadId).stream()
                .filter(candidate -> candidate.getStartIntersectionId()
                        .equals(reference.getEndIntersectionId()))
                .filter(candidate -> candidate.getEndIntersectionId()
                        .equals(reference.getStartIntersectionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Turnaround requires an exact reverse directed road"));

        Intersection start = network.getIntersection(reference.getStartIntersectionId());
        Intersection end = network.getIntersection(reference.getEndIntersectionId());
        GeoPoint geo = reference.getGeometry().isEmpty()
                ? interpolateGeographic(start, end, fractionAlongReferenceRoad)
                : PolylineGeometry.pointAt(
                        reference.getGeometry(), fractionAlongReferenceRoad);
        Intersection synthetic = new Intersection(
                id,
                "Turnaround " + id,
                interpolate(start.getX(), end.getX(), fractionAlongReferenceRoad),
                interpolate(start.getY(), end.getY(), fractionAlongReferenceRoad),
                LocationType.NORMAL,
                geo == null ? null : geo.latitude(),
                geo == null ? null : geo.longitude());

        SplitRoad referenceSplit = split(reference, id, fractionAlongReferenceRoad);
        SplitRoad reverseSplit = split(reverse, id, 1.0 - fractionAlongReferenceRoad);
        network.removeRoad(reference.getId());
        network.removeRoad(reverse.getId());
        network.addIntersection(synthetic);
        addSplit(network, referenceSplit);
        addSplit(network, reverseSplit);

        Set<VehicleType> allowed = allowedVehicleTypes == null
                ? Set.of()
                : Set.copyOf(allowedVehicleTypes);
        registerUTurn(
                network,
                id,
                referenceSplit.before().getId(),
                reverseSplit.after().getId(),
                allowed,
                penaltySeconds);
        registerUTurn(
                network,
                id,
                reverseSplit.before().getId(),
                referenceSplit.after().getId(),
                allowed,
                penaltySeconds);
    }

    private static void addSplit(RoadNetwork network, SplitRoad split) {
        network.addRoad(split.before());
        network.addRoad(split.after());
    }

    private static void registerUTurn(
            RoadNetwork network,
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId,
            Set<VehicleType> allowed,
            double penaltySeconds) {
        network.getMovementGraph().register(new JunctionMovement(
                junctionId,
                Optional.of(incomingRoadId),
                outgoingRoadId,
                TurnDirection.U_TURN,
                MovementPermission.ALLOWED,
                allowed,
                penaltySeconds,
                MovementProvenance.EXPLICIT));
    }

    private static SplitRoad split(
            Road road,
            String junctionId,
            double fraction) {
        String beforeId = road.getId() + "@" + junctionId + ":0";
        String afterId = road.getId() + "@" + junctionId + ":1";
        return new SplitRoad(
                copySegment(
                        road,
                        beforeId,
                        road.getStartIntersectionId(),
                        junctionId,
                        0.0,
                        fraction),
                copySegment(
                        road,
                        afterId,
                        junctionId,
                        road.getEndIntersectionId(),
                        fraction,
                        1.0));
    }

    private static Road copySegment(
            Road source,
            String id,
            String startIntersectionId,
            String endIntersectionId,
            double fromFraction,
            double toFraction) {
        double fraction = toFraction - fromFraction;
        RoadCondition condition = source.getCondition();
        RoadCondition copiedCondition = new RoadCondition(
                condition.getCongestionLevel(),
                condition.getVehicleDensity(),
                condition.getRoadStatus());
        return new Road(
                id,
                source.getName(),
                source.getPhysicalRoadId(),
                startIntersectionId,
                endIntersectionId,
                source.getDistance() * fraction,
                source.getSpeedLimit(),
                Math.max(1, (int) Math.round(source.getCapacity() * fraction)),
                0,
                source.getLaneCount(),
                source.getLaneWidthMetres(),
                copiedCondition,
                sampleGeometry(source.getGeometry(), fromFraction, toFraction),
                source.getRoadClass());
    }

    private static List<GeoPoint> sampleGeometry(
            List<GeoPoint> geometry,
            double fromFraction,
            double toFraction) {
        if (geometry == null || geometry.isEmpty()) return List.of();
        List<GeoPoint> result = new ArrayList<>();
        for (int index = 0; index < GEOMETRY_SAMPLES_PER_SEGMENT; index++) {
            double local = (double) index / (GEOMETRY_SAMPLES_PER_SEGMENT - 1);
            result.add(PolylineGeometry.pointAt(
                    geometry,
                    fromFraction + (toFraction - fromFraction) * local));
        }
        return List.copyOf(result);
    }

    private static GeoPoint interpolateGeographic(
            Intersection start,
            Intersection end,
            double fraction) {
        if (start.getLatitude() == null || end.getLatitude() == null) return null;
        return new GeoPoint(
                interpolate(start.getLatitude(), end.getLatitude(), fraction),
                interpolate(start.getLongitude(), end.getLongitude(), fraction));
    }

    private static double interpolate(double start, double end, double fraction) {
        return start + (end - start) * fraction;
    }

    private record SplitRoad(Road before, Road after) {}
}
