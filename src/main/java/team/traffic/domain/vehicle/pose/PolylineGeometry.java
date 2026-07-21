package team.traffic.domain.vehicle.pose;

import java.util.ArrayList;
import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * Measures and samples a geographic polyline.
 *
 * <p>A road's shape is a list of latitude/longitude points, but a vehicle's
 * progress along it is a fraction of its <em>length in metres</em>. Those two
 * are not the same thing: near the equator a degree of longitude is about as
 * long as a degree of latitude, but the further from it you go the shorter it
 * gets. Treating a polyline as if degrees were metres puts a vehicle in the
 * wrong place on any road that is not due north or due east.</p>
 *
 * <p>Distances use a local flat approximation, which is accurate to well under
 * a metre across a city and is far cheaper than a full geodesic.</p>
 */
public final class PolylineGeometry {

    /** Metres in one degree of latitude. Near enough constant everywhere. */
    private static final double METRES_PER_DEGREE_LATITUDE = 111_320.0;

    private PolylineGeometry() {
    }

    /** Length of the polyline in metres, or zero for fewer than two points. */
    public static double lengthMetres(List<GeoPoint> polyline) {
        if (polyline == null || polyline.size() < 2) {
            return 0.0;
        }
        double total = 0;
        for (int index = 1; index < polyline.size(); index++) {
            total += segmentLengthMetres(polyline.get(index - 1), polyline.get(index));
        }
        return total;
    }

    /**
     * Point at {@code fraction} of the way along the polyline, measured by
     * length rather than by point count. Returns {@code null} when the polyline
     * is empty.
     */
    public static GeoPoint pointAt(List<GeoPoint> polyline, double fraction) {
        if (polyline == null || polyline.isEmpty()) {
            return null;
        }
        if (polyline.size() == 1) {
            return polyline.get(0);
        }

        double clamped = clampFraction(fraction);
        double[] lengths = segmentLengths(polyline);
        double total = sum(lengths);
        if (total == 0) {
            return polyline.get(0);
        }

        double remaining = total * clamped;
        for (int index = 0; index < lengths.length; index++) {
            if (remaining <= lengths[index] || index == lengths.length - 1) {
                double within = lengths[index] == 0 ? 0 : remaining / lengths[index];
                return interpolate(polyline.get(index), polyline.get(index + 1), within);
            }
            remaining -= lengths[index];
        }
        return polyline.get(polyline.size() - 1);
    }

    /**
     * Compass bearing of the polyline at {@code fraction}, in degrees clockwise
     * from north. Uses the direction of the segment the point falls on, so a
     * vehicle points along the road it is actually on rather than along a chord
     * between sample points.
     */
    public static double bearingAt(List<GeoPoint> polyline, double fraction) {
        if (polyline == null || polyline.size() < 2) {
            return 0.0;
        }

        double clamped = clampFraction(fraction);
        double[] lengths = segmentLengths(polyline);
        double total = sum(lengths);
        if (total == 0) {
            return 0.0;
        }

        double remaining = total * clamped;
        for (int index = 0; index < lengths.length; index++) {
            if (remaining <= lengths[index] || index == lengths.length - 1) {
                return bearingBetween(polyline.get(index), polyline.get(index + 1));
            }
            remaining -= lengths[index];
        }
        return bearingBetween(polyline.get(polyline.size() - 2), polyline.get(polyline.size() - 1));
    }

    /** Compass bearing from one point to another, in degrees clockwise from north. */
    public static double bearingBetween(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            return 0.0;
        }
        double north = to.latitude() - from.latitude();
        double east = (to.longitude() - from.longitude())
                * Math.cos(Math.toRadians((from.latitude() + to.latitude()) / 2));
        if (north == 0 && east == 0) {
            return 0.0;
        }
        return (Math.toDegrees(Math.atan2(east, north)) + 360.0) % 360.0;
    }

    /**
     * Shortest signed turn from one bearing to another, in degrees within
     * {@code (-180, 180]}. Used to rotate a vehicle the short way round a
     * corner instead of spinning it most of the way about.
     */
    public static double bearingDifference(double fromDegrees, double toDegrees) {
        double difference = (toDegrees - fromDegrees + 540.0) % 360.0 - 180.0;
        return difference == -180.0 ? 180.0 : difference;
    }

    /** Bearing {@code fraction} of the way from one bearing to another, turning the short way. */
    public static double interpolateBearing(double fromDegrees, double toDegrees, double fraction) {
        double turned = fromDegrees + bearingDifference(fromDegrees, toDegrees) * clampFraction(fraction);
        return (turned % 360.0 + 360.0) % 360.0;
    }

    /** Straight two-point polyline, used when a road carries no stored shape. */
    public static List<GeoPoint> straightLine(GeoPoint from, GeoPoint to) {
        if (from == null || to == null) {
            return List.of();
        }
        List<GeoPoint> line = new ArrayList<>(2);
        line.add(from);
        line.add(to);
        return List.copyOf(line);
    }

    private static double[] segmentLengths(List<GeoPoint> polyline) {
        double[] lengths = new double[polyline.size() - 1];
        for (int index = 1; index < polyline.size(); index++) {
            lengths[index - 1] = segmentLengthMetres(polyline.get(index - 1), polyline.get(index));
        }
        return lengths;
    }

    private static double segmentLengthMetres(GeoPoint from, GeoPoint to) {
        double latitudeScale = Math.cos(Math.toRadians((from.latitude() + to.latitude()) / 2));
        double north = (to.latitude() - from.latitude()) * METRES_PER_DEGREE_LATITUDE;
        double east = (to.longitude() - from.longitude()) * METRES_PER_DEGREE_LATITUDE * latitudeScale;
        return Math.hypot(north, east);
    }

    private static GeoPoint interpolate(GeoPoint from, GeoPoint to, double fraction) {
        return new GeoPoint(
                from.latitude() + (to.latitude() - from.latitude()) * fraction,
                from.longitude() + (to.longitude() - from.longitude()) * fraction);
    }

    private static double sum(double[] values) {
        double total = 0;
        for (double value : values) {
            total += value;
        }
        return total;
    }

    private static double clampFraction(double fraction) {
        if (!Double.isFinite(fraction)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, fraction));
    }
}
