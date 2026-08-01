package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

/**
 * Arbitrates one junction for one simulation step.
 *
 * <p>Every vehicle that wants to cross registers its {@link JunctionApproach}.
 * The controller then sorts them by right of way and walks the list once,
 * clearing a movement only when it does not conflict with one already cleared,
 * the driver accepts the gap, and the road it leads onto physically has room.
 * Everyone else holds at the stop line and tries again next step.</p>
 *
 * <p>Resolving all approaches together, rather than letting each vehicle decide
 * as it is reached, is what keeps the outcome independent of the order the
 * vehicle list happens to be in.</p>
 */
public final class JunctionController {

    private final String junctionId;
    private final RightOfWayPolicy rightOfWayPolicy;
    private final GapAcceptanceModel gapAcceptanceModel;
    private final List<JunctionApproach> approaches = new ArrayList<>();

    public JunctionController(String junctionId, RightOfWayPolicy rightOfWayPolicy) {
        this(junctionId, rightOfWayPolicy, new GapAcceptanceModel());
    }

    public JunctionController(
            String junctionId,
            RightOfWayPolicy rightOfWayPolicy,
            GapAcceptanceModel gapAcceptanceModel) {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id is required.");
        }
        if (rightOfWayPolicy == null) {
            throw new IllegalArgumentException("Right-of-way policy is required.");
        }
        if (gapAcceptanceModel == null) {
            throw new IllegalArgumentException("Gap-acceptance model is required.");
        }
        this.junctionId = junctionId;
        this.rightOfWayPolicy = rightOfWayPolicy;
        this.gapAcceptanceModel = gapAcceptanceModel;
    }

    public String getJunctionId() {
        return junctionId;
    }

    public void register(JunctionApproach approach) {
        if (approach == null) {
            throw new IllegalArgumentException("Approach is required.");
        }
        if (!junctionId.equals(approach.junctionId())) {
            throw new IllegalArgumentException(
                    "Approach belongs to junction " + approach.junctionId() + ", not " + junctionId);
        }
        approaches.add(approach);
    }

    public List<JunctionApproach> getApproaches() {
        return List.copyOf(approaches);
    }

    /**
     * Returns the ids of the vehicles cleared to cross this step, in the order
     * they were cleared.
     */
    public Set<String> resolve(JunctionEnvironment environment) {
        Set<String> clearedVehicleIds = new LinkedHashSet<>();
        for (JunctionDecision decision : resolveDecisions(environment).values()) {
            if (decision.granted()) {
                clearedVehicleIds.add(decision.approach().vehicleId());
            }
        }
        return clearedVehicleIds;
    }

    /** Returns one auditable grant or denial for every registered approach. */
    public Map<String, JunctionDecision> resolveDecisions(JunctionEnvironment environment) {
        if (environment == null) {
            throw new IllegalArgumentException("Junction environment is required.");
        }

        List<JunctionApproach> ordered = new ArrayList<>(approaches);
        ordered.sort((left, right) -> {
            int reservationOrder = Boolean.compare(
                    environment.isRecoveryReserved(right),
                    environment.isRecoveryReserved(left));
            return reservationOrder != 0
                    ? reservationOrder
                    : rightOfWayPolicy.comparePriority(left, right);
        });

        List<JunctionApproach> cleared = new ArrayList<>();
        Map<String, JunctionDecision> decisions = new LinkedHashMap<>();
        Set<EntryChannel> capacityReservedForOlderVehicle = new LinkedHashSet<>();
        for (JunctionApproach candidate : ordered) {
            if (environment.isRecoveryHeld(candidate)
                    && !environment.isRecoveryReserved(candidate)) {
                decisions.put(candidate.vehicleId(), JunctionDecision.denied(
                        candidate, JunctionDenialReason.RECOVERY_HOLD));
                continue;
            }
            // Dispatch priority orders vehicles that are legally able to move;
            // it is not permission to run a red signal. Real pre-emption must
            // change the signal phase, not silently bypass it here.
            if (!environment.isSignalGreen(candidate)) {
                decisions.put(candidate.vehicleId(), JunctionDecision.denied(
                        candidate,
                        JunctionDenialReason.RED_SIGNAL,
                        environment.expectedSignalReleaseAt(candidate)));
                continue;
            }
            if (conflictsWithCleared(candidate, cleared)) {
                decisions.put(candidate.vehicleId(), JunctionDecision.denied(
                        candidate, JunctionDenialReason.CONFLICTING_MOVEMENT));
                continue;
            }
            if (!gapAcceptanceModel.hasAcceptableGap(candidate, environment)) {
                decisions.put(candidate.vehicleId(), JunctionDecision.denied(
                        candidate, JunctionDenialReason.UNSAFE_GAP));
                continue;
            }
            EntryChannel entryChannel = EntryChannel.of(candidate);
            if (capacityReservedForOlderVehicle.contains(entryChannel)) {
                decisions.put(candidate.vehicleId(), JunctionDecision.denied(
                        candidate, JunctionDenialReason.DOWNSTREAM_FULL));
                continue;
            }
            if (!environment.hasPhysicalSpaceFor(candidate)) {
                // Do not let a later, smaller participant repeatedly consume a
                // short entry gap that is insufficient for the older queue
                // head. Reserving only this outgoing lane lets the gap grow
                // until the older vehicle fits, while unrelated lanes keep
                // flowing. Without this no-barging rule a stream of filtering
                // motorcycles can starve a car or bus forever.
                capacityReservedForOlderVehicle.add(entryChannel);
                decisions.put(candidate.vehicleId(), JunctionDecision.denied(
                        candidate, JunctionDenialReason.DOWNSTREAM_FULL));
                continue;
            }
            cleared.add(candidate);
            decisions.put(candidate.vehicleId(), JunctionDecision.granted(candidate));
        }
        return Map.copyOf(decisions);
    }

    /** Physical continuation lane shared by approaches competing for entry space. */
    private record EntryChannel(String roadId, int laneIndex) {
        private static EntryChannel of(JunctionApproach approach) {
            int lastLaneIndex = Math.max(0, approach.toRoad().getLaneCount() - 1);
            return new EntryChannel(
                    approach.toRoad().getId(),
                    Math.min(approach.fromLaneIndex(), lastLaneIndex));
        }
    }

    private boolean conflictsWithCleared(JunctionApproach candidate, List<JunctionApproach> cleared) {
        for (JunctionApproach granted : cleared) {
            if (rightOfWayPolicy.conflicts(candidate, granted)) {
                return true;
            }
        }
        return false;
    }
}
