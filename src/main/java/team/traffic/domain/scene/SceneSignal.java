package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * The traffic lights at one junction, as the renderer needs them.
 *
 * <p>Without these a queue has no visible cause: vehicles stop at a line and
 * nothing on screen says why. Each controlled approach gets a head placed at
 * its own stop line, so a viewer can see which arm is being held and for how
 * much longer.</p>
 *
 * <p>The frame already decides all of this. Nothing here re-derives a phase;
 * it only carries what {@code SimulationFrame.SignalState} resolved for the
 * frame being drawn, so the lights on screen cannot disagree with the lights
 * the vehicles are obeying.</p>
 *
 * @param junctionId the intersection these lights control
 * @param approaches one entry per incoming road
 */
public record SceneSignal(String junctionId, List<Approach> approaches) {

    public SceneSignal {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id must not be blank.");
        }
        approaches = approaches == null ? List.of() : List.copyOf(approaches);
    }

    /**
     * One controlled arm of a junction.
     *
     * @param roadId          the incoming road being held or released
     * @param head            where to draw the light, at that road's stop line
     * @param green           whether this arm may proceed on the drawn frame
     * @param secondsRemaining time left in the current phase
     */
    public record Approach(String roadId, GeoPoint head, boolean green, double secondsRemaining) {

        public Approach {
            if (roadId == null || roadId.isBlank()) {
                throw new IllegalArgumentException("Approach road id must not be blank.");
            }
            if (head == null) {
                throw new IllegalArgumentException("Approach head position is required.");
            }
        }
    }
}
