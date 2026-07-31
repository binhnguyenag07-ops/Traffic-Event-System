package team.traffic.domain.network;

/**
 * A kerb-side stop at a point along one directed road.
 *
 * <p>Attached to a road rather than to a junction because that is what a bus
 * stop physically is: a place on one side of one street, which a bus pulls up
 * to and pulls away from without leaving the carriageway. The earlier model
 * held buses at junctions, which put every stop at an intersection and made a
 * bus stop wherever two streets happened to meet.</p>
 *
 * <p>Direction matters and is not an accident of the data. The stop for one
 * direction of travel sits across the street from the stop for the other, and
 * a bus travelling the wrong way past it must not serve it. Two directed roads
 * sharing a {@code physicalRoadId} therefore carry separate stops.</p>
 *
 * @param id                stable identifier, derived from the OSM node id
 * @param name              human-readable stop name, or a generated label when
 *                          OpenStreetMap records none
 * @param roadId            the directed road this stop serves
 * @param positionAlongRoad fraction from the road's start intersection, in
 *                          {@code [0, 1]}
 * @param dwellSeconds      how long a bus holds here for boarding
 */
public record BusStop(
        String id, String name, String roadId, double positionAlongRoad, double dwellSeconds) {

    /**
     * Dwell used when the map records a stop but no boarding time.
     *
     * <p>OpenStreetMap knows where a stop is and never how long a bus waits
     * there, so the map format leaves {@code dwellSeconds} optional and this
     * value stands in. It is the boarding time of a 40-seat bus.</p>
     */
    public static final double DEFAULT_DWELL_SECONDS = 11.0;

    /** Boarding time for a bus of this capacity, in seconds. */
    public static double dwellForPassengerCapacity(int passengerCapacity) {
        if (passengerCapacity <= 0) {
            throw new IllegalArgumentException("Passenger capacity must be positive.");
        }
        return 5.0 + passengerCapacity * 0.15;
    }

    public BusStop {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Bus-stop id is required.");
        }
        if (roadId == null || roadId.isBlank()) {
            throw new IllegalArgumentException("Bus-stop road id is required.");
        }
        if (!Double.isFinite(positionAlongRoad) || positionAlongRoad < 0.0 || positionAlongRoad > 1.0) {
            throw new IllegalArgumentException(
                    "Bus-stop position along road must be within [0, 1]: " + positionAlongRoad);
        }
        if (!Double.isFinite(dwellSeconds) || dwellSeconds <= 0) {
            throw new IllegalArgumentException("Bus-stop dwell time must be positive.");
        }
        name = name == null || name.isBlank() ? id : name;
    }

    /** Distance from the road's start to this stop, in metres. */
    public double distanceFromStartMetres(Road road) {
        if (road == null) throw new IllegalArgumentException("Road is required.");
        return positionAlongRoad * road.getDistance() * 1_000.0;
    }
}
