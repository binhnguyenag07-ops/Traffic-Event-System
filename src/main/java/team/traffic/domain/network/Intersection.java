package team.traffic.domain.network;

public class Intersection {
    private final String id;
    private final String name;
    private final double x;
    private final double y;
    private final LocationType locationType;

    // Creates one valid map point that can safely be used by routing and visualization.
    public Intersection(String id, String name, double x, double y, LocationType locationType) {
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
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.locationType = locationType;
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
}
