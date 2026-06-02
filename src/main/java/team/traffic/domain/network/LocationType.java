package team.traffic.domain.network;

public enum LocationType {
    SCHOOL(1.5, 1.0),
    HOSPITAL(1.2, 1.0),
    BAR(1.8, 3.0), // Quán nhậu sinh lưu lượng cao buổi tối, tỷ lệ gây tai nạn x3
    RESIDENTIAL(1.0, 1.0),
    COMMERCIAL(2.0, 1.2),
    NORMAL(1.0, 1.0);

    private final double baseTrafficDemand;
    private final double intoxicationFactor; // Hệ số tăng khả năng tai nạn

    LocationType(double baseTrafficDemand, double intoxicationFactor) {
        this.baseTrafficDemand = baseTrafficDemand;
        this.intoxicationFactor = intoxicationFactor;
    }

    public double getBaseTrafficDemand() {
        return baseTrafficDemand;
    }

    public double getIntoxicationFactor() {
        return intoxicationFactor;
    }
}
