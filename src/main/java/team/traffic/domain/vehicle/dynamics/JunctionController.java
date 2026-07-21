package team.traffic.domain.vehicle.dynamics;

import java.util.ArrayList;
import java.util.LinkedHashSet;
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
        if (environment == null) {
            throw new IllegalArgumentException("Junction environment is required.");
        }

        List<JunctionApproach> ordered = new ArrayList<>(approaches);
        ordered.sort(rightOfWayPolicy::comparePriority);

        List<JunctionApproach> cleared = new ArrayList<>();
        Set<String> clearedVehicleIds = new LinkedHashSet<>();
        for (JunctionApproach candidate : ordered) {
            if (conflictsWithCleared(candidate, cleared)) {
                continue;
            }
            if (!gapAcceptanceModel.hasAcceptableGap(candidate, environment)) {
                continue;
            }
            if (!environment.hasPhysicalSpaceFor(candidate)) {
                continue;
            }
            cleared.add(candidate);
            clearedVehicleIds.add(candidate.vehicleId());
        }
        return clearedVehicleIds;
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
