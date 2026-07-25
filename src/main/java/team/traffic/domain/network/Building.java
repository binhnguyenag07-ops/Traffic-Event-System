package team.traffic.domain.network;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A trip origin or destination located beside the road graph. */
public final class Building {
    private final String id;
    private final String name;
    private final BuildingType type;
    private final double x;
    private final double y;
    private final Double latitude;
    private final Double longitude;
    private final List<BuildingAccessPoint> accessPoints;
    private final List<GeoPoint> footprint;

    public Building(String id, String name, BuildingType type, double x, double y,
            Double latitude, Double longitude, List<BuildingAccessPoint> accessPoints) {
        this(id, name, type, x, y, latitude, longitude, accessPoints, List.of());
    }

    /**
     * A building with its ground outline, as OpenStreetMap records it.
     *
     * <p>The outline is carried for drawing only; routing reaches a building
     * through its access points, not its walls. It is optional, so a map that
     * knows only where a building stands still loads.</p>
     */
    public Building(String id, String name, BuildingType type, double x, double y,
            Double latitude, Double longitude, List<BuildingAccessPoint> accessPoints,
            List<GeoPoint> footprint) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Building id is required");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Building name is required");
        if (type == null) throw new IllegalArgumentException("Building type is required");
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("Building coordinates must be finite");
        }
        if ((latitude == null) != (longitude == null)) {
            throw new IllegalArgumentException("Building latitude and longitude must be provided together");
        }
        if (latitude != null) new GeoPoint(latitude, longitude);
        if (accessPoints == null || accessPoints.isEmpty()) {
            throw new IllegalArgumentException("Building must have at least one access point");
        }
        Set<String> accessIds = new LinkedHashSet<>();
        for (BuildingAccessPoint accessPoint : accessPoints) {
            if (accessPoint == null) throw new IllegalArgumentException("Building access point is required");
            if (!accessIds.add(accessPoint.id())) {
                throw new IllegalArgumentException("Duplicate building access id: " + accessPoint.id());
            }
        }
        this.id = id;
        this.name = name;
        this.type = type;
        this.x = x;
        this.y = y;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accessPoints = List.copyOf(accessPoints);
        this.footprint = footprint == null ? List.of() : List.copyOf(footprint);
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public BuildingType getType() { return type; }
    public double getX() { return x; }
    public double getY() { return y; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public List<BuildingAccessPoint> getAccessPoints() { return accessPoints; }

    /** Ground outline for drawing, empty when the map carries only a position. */
    public List<GeoPoint> getFootprint() { return footprint; }
}
