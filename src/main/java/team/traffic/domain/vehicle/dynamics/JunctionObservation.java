package team.traffic.domain.vehicle.dynamics;

import java.util.Optional;

/** Visible junction clearance and downstream-space evidence. */
public record JunctionObservation(
        String junctionId,
        Optional<JunctionDenialReason> denialReason,
        boolean downstreamEntrySpace) {

    public JunctionObservation {
        if (junctionId == null || junctionId.isBlank()) {
            throw new IllegalArgumentException("Junction id is required.");
        }
        denialReason = denialReason == null ? Optional.empty() : denialReason;
    }
}
