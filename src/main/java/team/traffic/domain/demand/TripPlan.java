package team.traffic.domain.demand;

import java.util.List;
import team.traffic.domain.vehicle.VehicleType;

/** Fully resolved, deterministic journey consumed by the vehicle runtime. */
public record TripPlan(
        String id,
        PurposeId purpose,
        VehicleType vehicleType,
        ResolvedPlace origin,
        List<TripLeg> legs,
        CompletionPolicy completion,
        double departAtSeconds) {

    public TripPlan {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Trip plan id is required");
        if (purpose == null) throw new IllegalArgumentException("Trip plan purpose is required");
        if (vehicleType == null) throw new IllegalArgumentException("Trip plan vehicle type is required");
        if (origin == null) throw new IllegalArgumentException("Trip plan origin is required");
        if (legs == null || legs.isEmpty()) throw new IllegalArgumentException("Trip plan requires at least one leg");
        legs = List.copyOf(legs);
        if (completion == null) throw new IllegalArgumentException("Trip plan completion is required");
        if (!Double.isFinite(departAtSeconds) || departAtSeconds < 0) {
            throw new IllegalArgumentException("Trip plan departure must be finite and non-negative");
        }
        if (completion == CompletionPolicy.RETURN_TO_ORIGIN) {
            TripLeg last = legs.get(legs.size() - 1);
            if (!last.to().equals(origin)) {
                throw new IllegalArgumentException("Return-to-origin plan must end at its exact origin");
            }
        }
    }
}
