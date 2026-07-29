package team.traffic.domain.vehicle;

/**
 * Coarse physical phase of a journey, intended for renderers and diagnostics.
 *
 * <p>The road model remains authoritative. Off-network phases merely expose
 * when a vehicle is at a resolved building or boundary source so a client can
 * draw the driveway/entry transition instead of inventing a road spawn.</p>
 */
public enum JourneyPhase {
    DEPARTING_ORIGIN,
    ON_ROAD,
    AT_JUNCTION,
    ARRIVING_DESTINATION,
    DWELLING,
    ARRIVED
}
