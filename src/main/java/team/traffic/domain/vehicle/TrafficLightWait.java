package team.traffic.domain.vehicle;

import java.util.HashSet;
import java.util.Set;

/**
 * Holds a vehicle at a signal for a set number of ticks, once per signal.
 *
 * <p>Its own small state machine: which signal is currently holding the
 * vehicle, how much longer, and which signals it has already served. Keeping
 * that here rather than among the vehicle's own fields means the rule that a
 * signal delays a given vehicle at most once can be read, and tested, in one
 * place.</p>
 */
public final class TrafficLightWait {

    private String activeEventId;
    private long ticksRemaining;
    private final Set<String> servedEventIds = new HashSet<>();

    /**
     * Counts down one tick against a signal.
     *
     * @return {@code true} while the vehicle must stay put; {@code false} once
     *         the delay is served, or for a signal it has already served
     */
    public boolean hold(String eventId, long delayTicks) {
        if (eventId == null || eventId.isBlank() || delayTicks <= 0 || servedEventIds.contains(eventId)) {
            return false;
        }

        if (!eventId.equals(activeEventId)) {
            activeEventId = eventId;
            ticksRemaining = delayTicks;
        }
        if (ticksRemaining <= 0) {
            return false;
        }

        ticksRemaining--;
        if (ticksRemaining == 0) {
            servedEventIds.add(eventId);
            activeEventId = null;
        }
        return true;
    }

    /** Releases the vehicle, for instance when the signal no longer applies. */
    public void clear() {
        activeEventId = null;
        ticksRemaining = 0;
    }

    public long getTicksRemaining() {
        return ticksRemaining;
    }

    public boolean hasServed(String eventId) {
        return servedEventIds.contains(eventId);
    }
}
