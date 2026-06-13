package main.java.team.traffic.domain.network;

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
        this.locationType = locationType;
    }
}
