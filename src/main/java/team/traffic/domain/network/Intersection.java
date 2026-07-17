package team.traffic.domain.network;

public class Intersection {
    private final String id;
    private final String name;
    private final double x;
    private final double y;
    private final LocationType locationType;
    private final Double latitude;
    private final Double longitude;

    // Creates one valid map point that can safely be used by routing and visualization.
    public Intersection(String id, String name, double x, double y, LocationType locationType) {
        this(id, name, x, y, locationType, null, null);
    }

    public Intersection(String id, String name, double x, double y, LocationType locationType,
            Double latitude, Double longitude) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Intersection id is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Intersection name is required");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Intersection coordinates must be finite");
        }
        if (locationType == null) {
            throw new IllegalArgumentException("Location type is required");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Latitude and longitude must be provided together");
        }
        if (latitude != null && (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180)) {
            throw new IllegalArgumentException("Geographic coordinates are invalid");
        }
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.locationType = locationType;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public LocationType getLocationType() {
        return locationType;
    }

    public Double getLatitude() { return latitude; }

    public Double getLongitude() { return longitude; }
}
