package team.traffic.domain.vehicle.dynamics;

/**
 * Lets a narrow vehicle ride the gap between lanes instead of queueing in one.
 *
 * <p>Ordinary lane-change models answer "which lane?", and for a motorcycle in
 * stopped traffic every answer is wrong: both lanes are blocked, so it waits,
 * while the space it would actually use — the 1.7 m between two cars on
 * adjacent lane centres — is not somewhere the model can put it.</p>
 *
 * <p>This model answers "where?" instead. It scores the lane centres and the
 * boundaries between them alike, keeping whichever corridor is physically free
 * and gets it moving fastest. A corridor is free when nothing alongside
 * overlaps it, which is arithmetic on widths rather than a special case for
 * motorcycles: the same check refuses the corridor to anything too wide for
 * it.</p>
 *
 * <p>Lane centres win ties, so a rider with a clear road ahead sits in a lane
 * like everybody else and filters only when it buys something.</p>
 */
public final class FilteringLaneChangeModel implements LaneChangeModel {

    /**
     * Improvement required before leaving the current position. Without it a
     * rider would swap between two equally good corridors every step.
     */
    private static final double INCENTIVE_THRESHOLD_MPS2 = 0.05;

    private final FilteringRules rules;

    public FilteringLaneChangeModel() {
        this(FilteringRules.defaults());
    }

    public FilteringLaneChangeModel(FilteringRules rules) {
        if (rules == null) {
            throw new IllegalArgumentException("Filtering rules are required.");
        }
        this.rules = rules;
    }

    public FilteringRules getRules() {
        return rules;
    }

    @Override
    public LateralTarget chooseTarget(LaneChangeRequest request) {
        TrafficParticipant vehicle = request.vehicle();
        RoadOccupancy occupancy = request.occupancy();
        double currentOffset = vehicle.getLateralOffsetMetres();
        if (occupancy.getLaneCount() <= 0) {
            return LateralTarget.ofOffset(currentOffset, occupancy);
        }

        double bestOffset = currentOffset;
        double bestScore = accelerationAt(request, currentOffset) + INCENTIVE_THRESHOLD_MPS2;
        boolean bestIsLaneCentre = true;

        // A gap beside someone still crossing a line is about to close, so the
        // rider holds position rather than aiming into it.
        boolean corridorsUnstable = occupancy.hasManoeuvringNeighbour(
                vehicle, rules.neighbourWindowMetres());

        for (Candidate candidate : candidates(occupancy)) {
            if (candidate.isBetweenLanes() && corridorsUnstable) continue;
            if (!occupancy.isLaterallyClear(vehicle, candidate.offsetMetres(),
                    rules.lateralClearanceMetres(), rules.neighbourWindowMetres())) {
                continue;
            }
            double score = accelerationAt(request, candidate.offsetMetres());
            if (score > bestScore + 1e-12
                    || Math.abs(score - bestScore) <= 1e-12
                            && !bestIsLaneCentre && !candidate.isBetweenLanes()) {
                bestScore = score;
                bestOffset = candidate.offsetMetres();
                bestIsLaneCentre = !candidate.isBetweenLanes();
            }
        }
        return LateralTarget.ofOffset(bestOffset, occupancy);
    }

    @Override
    public int chooseTargetLane(LaneChangeRequest request) {
        return chooseTarget(request).laneIndex();
    }

    @Override
    public String getName() {
        return "MOTORCYCLE_SUBLANE_FILTERING";
    }

    /** Lane centres first so they win ties, then the boundaries between them. */
    private static Candidate[] candidates(RoadOccupancy occupancy) {
        int laneCount = occupancy.getLaneCount();
        Candidate[] candidates = new Candidate[Math.max(0, laneCount + laneCount - 1)];
        for (int index = 0; index < laneCount; index++) {
            candidates[index] = new Candidate(occupancy.laneCentreOffsetMetres(index), false);
        }
        for (int index = 0; index < laneCount - 1; index++) {
            double between = (occupancy.laneCentreOffsetMetres(index)
                    + occupancy.laneCentreOffsetMetres(index + 1)) / 2.0;
            candidates[laneCount + index] = new Candidate(between, true);
        }
        return candidates;
    }

    private double accelerationAt(LaneChangeRequest request, double offsetMetres) {
        LeaderGap gap = request.occupancy().findLeaderAtOffset(
                request.vehicle(), offsetMetres, rules.lateralClearanceMetres());
        return MobilLaneChangeModel.predictedAcceleration(request.vehicle(), gap, request);
    }

    private record Candidate(double offsetMetres, boolean isBetweenLanes) {
    }
}
