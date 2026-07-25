package team.traffic.domain.scene;

import java.util.Set;

/**
 * What a frame says about one junction's lights, before the scene decides where
 * to draw them.
 *
 * <p>The caller resolves this from the authoritative frame; the assembler turns
 * it into {@link SceneSignal} by placing a head on each approach's stop line.
 * Keeping the two apart means this package needs no knowledge of the simulation
 * history, and the renderer gets positions it does not have to work out.</p>
 *
 * @param junctionId            the controlled intersection
 * @param greenIncomingRoadIds  roads released on the frame being drawn
 * @param secondsRemaining      time left in the current phase
 */
public record SignalSnapshot(
        String junctionId,
        Set<String> greenIncomingRoadIds,
        double secondsRemaining) {

    public SignalSnapshot {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id must not be blank.");
        }
        greenIncomingRoadIds = greenIncomingRoadIds == null ? Set.of() : Set.copyOf(greenIncomingRoadIds);
    }
}
