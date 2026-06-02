package team.traffic.domain.network;

public class Intersection {
    private String id;
    private String name;
    private double x;
    private double y;
    private LocationType locationType;

    public Intersection(String id, String name, double x, double y, LocationType locationType) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.locationType = locationType != null ? locationType : LocationType.NORMAL;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public double getX() { return x; }
    public double getY() { return y; }
    public LocationType getLocationType() { return locationType; }
    
    public void setLocationType(LocationType locationType) {
        this.locationType = locationType;
    }
}
