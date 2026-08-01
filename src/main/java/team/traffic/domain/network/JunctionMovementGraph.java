package team.traffic.domain.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import team.traffic.domain.vehicle.VehicleType;
import team.traffic.domain.vehicle.dynamics.TurnDirection;

/**
 * Authoritative legal-manoeuvre index.
 *
 * <p>Maps may register explicit rules. Missing ordinary movements are inferred
 * from directed connectivity; reverse movements are inferred only for local
 * two-way streets when the configured policy permits it.</p>
 */
public final class JunctionMovementGraph {
    public static final double DEFAULT_UTURN_PENALTY_SECONDS = 25.0;

    private final Map<MovementKey, JunctionMovement> explicitMovements = new LinkedHashMap<>();
    private RoadNetwork attachedNetwork;
    private UTurnNetworkPolicy uTurnPolicy;
    private double inferredUTurnPenaltySeconds;

    public JunctionMovementGraph() {
        this(UTurnNetworkPolicy.EXPLICIT_ONLY, DEFAULT_UTURN_PENALTY_SECONDS);
    }

    JunctionMovementGraph(RoadNetwork network) {
        this();
        this.attachedNetwork = Objects.requireNonNull(network, "Road network is required.");
    }

    public JunctionMovementGraph(
            UTurnNetworkPolicy uTurnPolicy,
            double inferredUTurnPenaltySeconds) {
        this.uTurnPolicy = Objects.requireNonNull(uTurnPolicy, "U-turn policy is required.");
        if (!Double.isFinite(inferredUTurnPenaltySeconds) || inferredUTurnPenaltySeconds < 0) {
            throw new IllegalArgumentException("U-turn penalty must be finite and non-negative.");
        }
        this.inferredUTurnPenaltySeconds = inferredUTurnPenaltySeconds;
    }

    public synchronized void register(JunctionMovement movement) {
        Objects.requireNonNull(movement, "Movement is required.");
        validateReferences(movement);
        explicitMovements.put(MovementKey.of(movement), movement);
    }

    public synchronized boolean remove(
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId) {
        return explicitMovements.remove(
                new MovementKey(junctionId, incomingRoadId, outgoingRoadId)) != null;
    }

    public synchronized void removeRulesForRoad(String roadId) {
        if (roadId == null) {
            return;
        }
        explicitMovements.entrySet().removeIf(entry ->
                roadId.equals(entry.getValue().toRoadId())
                        || entry.getValue().fromRoadId().filter(roadId::equals).isPresent());
    }

    public synchronized List<JunctionMovement> explicitMovements() {
        return List.copyOf(explicitMovements.values());
    }

    public synchronized void setUTurnPolicy(UTurnNetworkPolicy uTurnPolicy) {
        this.uTurnPolicy = Objects.requireNonNull(uTurnPolicy, "U-turn policy is required.");
    }

    public synchronized UTurnNetworkPolicy getUTurnPolicy() {
        return uTurnPolicy;
    }

    public synchronized void setInferredUTurnPenaltySeconds(double penaltySeconds) {
        if (!Double.isFinite(penaltySeconds) || penaltySeconds < 0) {
            throw new IllegalArgumentException(
                    "U-turn penalty must be finite and non-negative.");
        }
        inferredUTurnPenaltySeconds = penaltySeconds;
    }

    public synchronized double getInferredUTurnPenaltySeconds() {
        return inferredUTurnPenaltySeconds;
    }

    public synchronized List<JunctionMovement> movementsFrom(
            RoadNetwork network,
            String junctionId,
            String incomingRoadId,
            VehicleType vehicleType) {
        Objects.requireNonNull(network, "Road network is required.");
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id is required.");
        }
        List<JunctionMovement> result = new ArrayList<>();
        for (Road outgoing : network.findOutgoingRoads(junctionId)) {
            JunctionMovement movement = movement(
                    network, junctionId, incomingRoadId, outgoing.getId());
            if (movement != null && movement.allows(vehicleType)) {
                result.add(movement);
            }
        }
        result.sort(Comparator.comparing(JunctionMovement::toRoadId));
        return List.copyOf(result);
    }

    public synchronized List<JunctionMovement> movementsFrom(
            String junctionId,
            String incomingRoadId,
            VehicleType vehicleType) {
        return movementsFrom(requireAttachedNetwork(), junctionId, incomingRoadId, vehicleType);
    }

    public synchronized Optional<JunctionMovement> find(
            RoadNetwork network,
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId) {
        return Optional.ofNullable(movement(network, junctionId, incomingRoadId, outgoingRoadId));
    }

    public synchronized Optional<JunctionMovement> find(
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId) {
        return find(requireAttachedNetwork(), junctionId, incomingRoadId, outgoingRoadId);
    }

    public synchronized boolean isAllowed(
            RoadNetwork network,
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId,
            VehicleType vehicleType) {
        JunctionMovement movement = movement(
                network, junctionId, incomingRoadId, outgoingRoadId);
        return movement != null && movement.allows(vehicleType);
    }

    public synchronized boolean isAllowed(
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId,
            VehicleType vehicleType) {
        return isAllowed(
                requireAttachedNetwork(),
                junctionId,
                incomingRoadId,
                outgoingRoadId,
                vehicleType);
    }

    public synchronized RoadNetwork attachedNetwork() {
        return requireAttachedNetwork();
    }

    private JunctionMovement movement(
            RoadNetwork network,
            String junctionId,
            String incomingRoadId,
            String outgoingRoadId) {
        MovementKey key = new MovementKey(junctionId, incomingRoadId, outgoingRoadId);
        JunctionMovement explicit = explicitMovements.get(key);
        if (explicit != null) {
            return explicit;
        }

        Road outgoing = network.getRoad(outgoingRoadId);
        if (outgoing == null || !junctionId.equals(outgoing.getStartIntersectionId())) {
            return null;
        }
        if (incomingRoadId == null) {
            return inferred(junctionId, null, outgoing, TurnDirection.STRAIGHT, 0.0);
        }

        Road incoming = network.getRoad(incomingRoadId);
        if (incoming == null || !junctionId.equals(incoming.getEndIntersectionId())) {
            return null;
        }
        TurnDirection direction = directionOf(network, incoming, outgoing);
        if (direction == TurnDirection.U_TURN) {
            if (canInferOrdinaryUTurn(incoming, outgoing)) {
                return inferred(
                        junctionId,
                        incomingRoadId,
                        outgoing,
                        direction,
                        inferredUTurnPenaltySeconds);
            }
            if (canInferEmergencyUTurn(incoming, outgoing)) {
                // Emergency services may turn across a major road under the
                // same junction arbitration as any other conflicting movement.
                // Restricting the inferred rule to EMERGENCY keeps ordinary
                // routing and map legality unchanged.
                return inferred(
                        junctionId,
                        incomingRoadId,
                        outgoing,
                        direction,
                        inferredUTurnPenaltySeconds,
                        java.util.Set.of(VehicleType.EMERGENCY));
            }
            return null;
        }
        return inferred(junctionId, incomingRoadId, outgoing, direction, 0.0);
    }

    private JunctionMovement inferred(
            String junctionId,
            String incomingRoadId,
            Road outgoing,
            TurnDirection direction,
            double penaltySeconds) {
        return inferred(
                junctionId,
                incomingRoadId,
                outgoing,
                direction,
                penaltySeconds,
                java.util.Set.of());
    }

    private JunctionMovement inferred(
            String junctionId,
            String incomingRoadId,
            Road outgoing,
            TurnDirection direction,
            double penaltySeconds,
            java.util.Set<VehicleType> allowedVehicleTypes) {
        return new JunctionMovement(
                junctionId,
                Optional.ofNullable(incomingRoadId),
                outgoing.getId(),
                direction,
                MovementPermission.ALLOWED,
                allowedVehicleTypes,
                penaltySeconds,
                MovementProvenance.INFERRED);
    }

    private boolean canInferOrdinaryUTurn(Road incoming, Road outgoing) {
        if (!canInferEmergencyUTurn(incoming, outgoing)) return false;
        return switch (incoming.getRoadClass()) {
            case RESIDENTIAL, LIVING_STREET, UNCLASSIFIED -> true;
            default -> false;
        };
    }

    private boolean canInferEmergencyUTurn(Road incoming, Road outgoing) {
        if (uTurnPolicy != UTurnNetworkPolicy.EXPLICIT_OR_CONSERVATIVE_LOCAL_INFERENCE) {
            return false;
        }
        if (!incoming.getStartIntersectionId().equals(outgoing.getEndIntersectionId())) {
            return false;
        }
        return incoming.getPhysicalRoadId().equals(outgoing.getPhysicalRoadId());
    }

    private static TurnDirection directionOf(
            RoadNetwork network,
            Road incoming,
            Road outgoing) {
        if (incoming.getStartIntersectionId().equals(outgoing.getEndIntersectionId())) {
            return TurnDirection.U_TURN;
        }
        Intersection from = network.getIntersection(incoming.getStartIntersectionId());
        Intersection via = network.getIntersection(incoming.getEndIntersectionId());
        Intersection to = network.getIntersection(outgoing.getEndIntersectionId());
        TurnDirection geometric = TurnDirection.of(from, via, to);
        // A hairpin onto a different directed road is not the reverse-road
        // manoeuvre governed by U-turn permission.
        return geometric == TurnDirection.U_TURN ? TurnDirection.LEFT : geometric;
    }

    private RoadNetwork requireAttachedNetwork() {
        if (attachedNetwork == null) {
            throw new IllegalStateException("Movement graph is not attached to a road network.");
        }
        return attachedNetwork;
    }

    private void validateReferences(JunctionMovement movement) {
        if (attachedNetwork == null) return;
        if (!attachedNetwork.containsIntersection(movement.junctionId())) {
            throw new IllegalArgumentException(
                    "Movement junction does not exist: " + movement.junctionId());
        }
        Road outgoing = attachedNetwork.getRoad(movement.toRoadId());
        if (outgoing == null
                || !movement.junctionId().equals(outgoing.getStartIntersectionId())) {
            throw new IllegalArgumentException(
                    "Movement target road must leave its junction: "
                            + movement.toRoadId());
        }
        movement.fromRoadId().ifPresent(incomingRoadId -> {
            Road incoming = attachedNetwork.getRoad(incomingRoadId);
            if (incoming == null
                    || !movement.junctionId().equals(incoming.getEndIntersectionId())) {
                throw new IllegalArgumentException(
                        "Movement source road must enter its junction: "
                                + incomingRoadId);
            }
        });
    }

    private record MovementKey(String junctionId, String incomingRoadId, String outgoingRoadId) {
        private static MovementKey of(JunctionMovement movement) {
            return new MovementKey(
                    movement.junctionId(),
                    movement.fromRoadId().orElse(null),
                    movement.toRoadId());
        }
    }
}
