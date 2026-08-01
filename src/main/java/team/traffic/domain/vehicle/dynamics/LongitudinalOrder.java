package team.traffic.domain.vehicle.dynamics;

/**
 * Stable front-to-back ordering for participants that occupy one road.
 *
 * <p>Microscopic integration can put two front bumpers at exactly the stop
 * line in the same captured frame. Treating {@code >=} as "ahead" makes both
 * participants see the other as their leader, so neither can become the queue
 * head and an empty junction exit remains unused forever. Position remains the
 * physical ordering; the stable participant id only breaks an exact tie.</p>
 */
final class LongitudinalOrder {
    private LongitudinalOrder() {}

    static boolean isAhead(
            TrafficParticipant candidate,
            double candidatePositionMetres,
            TrafficParticipant subject,
            double subjectPositionMetres) {
        int positionOrder = Double.compare(candidatePositionMetres, subjectPositionMetres);
        return positionOrder > 0
                || positionOrder == 0
                        && candidate.getId().compareTo(subject.getId()) < 0;
    }
}
