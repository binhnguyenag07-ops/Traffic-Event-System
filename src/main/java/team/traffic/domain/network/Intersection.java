package team.traffic.domain.network;

public class Intersection {
    private final String id;
    private final String name;
    private final double longitude;
    private final double latitude;
    private final LocationType legacyLocationType;

    /** Creates a road junction that can be used by routing and visualization. */
    public Intersection(String id, String name, double longitude, double latitude) {
        this.id = requireId(id);
        this.name = requireName(name);
        validateCoordinates(longitude, latitude);
        this.longitude = longitude;
        this.latitude = latitude;
        this.legacyLocationType = LocationType.NORMAL;
    }

    /**
     * Compatibility constructor for old scenario files and callers.
     * Location type is retained only for old callers and is excluded from the
     * new persistence schema because a junction is not a point of interest.
     *
     * @deprecated use {@link #Intersection(String, String, double, double)}
     */
    @Deprecated
    public Intersection(String id, String name, double longitude, double latitude, LocationType ignoredLocationType) {
        this.id = requireId(id);
        this.name = requireName(name);
        validateCoordinates(longitude, latitude);
        this.longitude = longitude;
        this.latitude = latitude;
        this.legacyLocationType = ignoredLocationType == null ? LocationType.NORMAL : ignoredLocationType;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getLatitude() {
        return latitude;
    }

    /** @deprecated use {@link #getLongitude()} */
    @Deprecated
    public double getX() {
        return longitude;
    }

    /** @deprecated use {@link #getLatitude()} */
    @Deprecated
    public double getY() {
        return latitude;
    }

    /**
     * Compatibility only. Intersections no longer carry point-of-interest
     * semantics.
     *
     * @deprecated remove callers and use a separate POI model if needed
     */
    @Deprecated
    public LocationType getLocationType() {
        return legacyLocationType;
    }

    private static String requireId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Intersection id is required");
        }
        return id;
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Intersection name is required");
        }
        return name;
    }

    private static void validateCoordinates(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
            throw new IllegalArgumentException("Intersection coordinates must be finite");
        }
    }
}
