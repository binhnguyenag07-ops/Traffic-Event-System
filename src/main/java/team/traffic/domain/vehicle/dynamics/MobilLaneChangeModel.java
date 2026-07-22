package team.traffic.domain.vehicle.dynamics;

/**
 * Compact MOBIL implementation: a lane change must be safe for the new
 * follower and its acceleration benefit must exceed the politeness-weighted
 * inconvenience imposed on that follower.
 */
public final class MobilLaneChangeModel implements LaneChangeModel {
    public static final double DEFAULT_INCENTIVE_THRESHOLD_MPS2 = 0.2;

    @Override
    public int chooseTargetLane(LaneChangeRequest request) {
        TrafficParticipant vehicle = request.vehicle();
        int currentIndex = vehicle.getLaneIndex();
        Lane current = request.occupancy().lane(currentIndex);
        if (current == null || request.occupancy().getLaneCount() <= 1) return currentIndex;

        double currentAcceleration = predictedAcceleration(vehicle, current.findLeaderFor(vehicle), request);
        int bestIndex = currentIndex;
        double bestIncentive = thresholdFor(vehicle);

        for (int candidateIndex : adjacentLaneIndexes(currentIndex, request.occupancy().getLaneCount())) {
            Lane candidate = request.occupancy().lane(candidateIndex);
            if (!safeForFollower(vehicle, candidate)) continue;
            double ownAcceleration = predictedAcceleration(vehicle, candidate.findLeaderFor(vehicle), request);
            TrafficParticipant follower = candidate.findFollowerBehind(vehicle);
            double followerImpact = follower == null ? 0.0 : followerAccelerationImpact(follower, vehicle, candidate, request);
            double incentive = ownAcceleration - currentAcceleration
                    + vehicle.getDriverProfile().politeness() * followerImpact;
            if (incentive > bestIncentive + 1e-12
                    || Math.abs(incentive - bestIncentive) <= 1e-12 && candidateIndex < bestIndex) {
                bestIncentive = incentive;
                bestIndex = candidateIndex;
            }
        }
        return bestIndex;
    }

    @Override
    public String getName() {
        return "MOBIL";
    }

    private static int[] adjacentLaneIndexes(int current, int count) {
        if (count <= 1) return new int[0];
        if (current <= 0) return new int[] {1};
        if (current >= count - 1) return new int[] {count - 2};
        return new int[] {current - 1, current + 1};
    }

    private static double thresholdFor(TrafficParticipant vehicle) {
        return vehicle.getPriorityLevel() > 0 ? 0.0 : DEFAULT_INCENTIVE_THRESHOLD_MPS2;
    }

    private static boolean safeForFollower(TrafficParticipant entering, Lane target) {
        TrafficParticipant follower = target.findFollowerBehind(entering);
        if (follower == null) return true;
        double enteringRear = target.positionMetresOf(entering) - entering.getLength();
        double usableGap = enteringRear - target.positionMetresOf(follower)
                - follower.getDriverProfile().minimumGapMetres();
        if (usableGap <= 0) return false;
        double closingSpeed = follower.getInstantaneousSpeedMps() - entering.getInstantaneousSpeedMps();
        if (closingSpeed <= 0) return true;
        double requiredDeceleration = closingSpeed * closingSpeed / (2.0 * usableGap);
        return requiredDeceleration <= follower.getDecelerationMps2();
    }

    private static double followerAccelerationImpact(
            TrafficParticipant follower,
            TrafficParticipant entering,
            Lane target,
            LaneChangeRequest request) {
        LeaderGap before = target.findLeaderFor(follower);
        double beforeAcceleration = predictedAcceleration(follower, before, request);
        double gap = target.positionMetresOf(entering) - entering.getLength()
                - target.positionMetresOf(follower)
                - follower.getDriverProfile().minimumGapMetres();
        LeaderGap after = new LeaderGap(gap, entering.getInstantaneousSpeedMps());
        double afterAcceleration = predictedAcceleration(follower, after, request);
        return afterAcceleration - beforeAcceleration;
    }

    private static double predictedAcceleration(
            TrafficParticipant participant,
            LeaderGap gap,
            LaneChangeRequest request) {
        double safe = KraussCarFollowingModel.safeSpeedMps(
                gap.gapMetres(), gap.leaderSpeedMps(), participant.getInstantaneousSpeedMps(),
                participant.getDecelerationMps2(), participant.getDriverProfile().reactionTimeSeconds());
        double reachable = participant.getInstantaneousSpeedMps()
                + participant.getAccelerationMps2() * request.timeStepSeconds();
        double next = Math.min(request.desiredSpeedMps(), Math.min(reachable, safe));
        return (Math.max(0.0, next) - participant.getInstantaneousSpeedMps()) / request.timeStepSeconds();
    }
}
