package team.traffic.domain.vehicle.pose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;

class PolylineGeometryTest {

    private static final GeoPoint ORIGIN = new GeoPoint(10.77, 106.70);
    private static final GeoPoint NORTH = new GeoPoint(10.78, 106.70);
    private static final GeoPoint EAST = new GeoPoint(10.77, 106.71);

    @Test
    void aDegreeOfLatitudeIsAboutOneHundredAndElevenKilometres() {
        assertThat(PolylineGeometry.lengthMetres(List.of(ORIGIN, NORTH)))
                .isCloseTo(1113.2, within(1.0));
    }

    @Test
    void aDegreeOfLongitudeIsShorterThanADegreeOfLatitudeAwayFromTheEquator() {
        double northSouth = PolylineGeometry.lengthMetres(List.of(ORIGIN, NORTH));
        double eastWest = PolylineGeometry.lengthMetres(List.of(ORIGIN, EAST));

        // At 10.77 degrees north the shrink factor is cos(10.77) = 0.982.
        assertThat(eastWest).isLessThan(northSouth);
        assertThat(eastWest / northSouth).isCloseTo(Math.cos(Math.toRadians(10.775)), within(0.001));
    }

    @Test
    void theMidpointOfAStraightLineIsHalfway() {
        GeoPoint midpoint = PolylineGeometry.pointAt(List.of(ORIGIN, NORTH), 0.5);

        assertThat(midpoint.latitude()).isCloseTo(10.775, within(1e-9));
        assertThat(midpoint.longitude()).isCloseTo(106.70, within(1e-9));
    }

    @Test
    void progressIsMeasuredByLengthNotByPointCount() {
        // A long first segment, then a short one a hundredth of its length.
        List<GeoPoint> polyline = List.of(
                new GeoPoint(10.77, 106.70),
                new GeoPoint(10.79, 106.70),
                new GeoPoint(10.7902, 106.70));

        GeoPoint halfway = PolylineGeometry.pointAt(polyline, 0.5);

        // Counting points would put halfway at the middle vertex, 10.79.
        // Measuring length puts it near the middle of the long first segment.
        assertThat(halfway.latitude()).isCloseTo(10.7801, within(0.0002));
    }

    @Test
    void headingNorthIsZeroAndHeadingEastIsNinety() {
        assertThat(PolylineGeometry.bearingBetween(ORIGIN, NORTH)).isCloseTo(0.0, within(0.01));
        assertThat(PolylineGeometry.bearingBetween(ORIGIN, EAST)).isCloseTo(90.0, within(0.01));
        assertThat(PolylineGeometry.bearingBetween(NORTH, ORIGIN)).isCloseTo(180.0, within(0.01));
        assertThat(PolylineGeometry.bearingBetween(EAST, ORIGIN)).isCloseTo(270.0, within(0.01));
    }

    @Test
    void bearingFollowsTheSegmentTheVehicleIsActuallyOn() {
        // North for the first half, then east for the second.
        List<GeoPoint> corner = List.of(ORIGIN, NORTH, new GeoPoint(10.78, 106.7098));

        assertThat(PolylineGeometry.bearingAt(corner, 0.1)).isCloseTo(0.0, within(1.0));
        assertThat(PolylineGeometry.bearingAt(corner, 0.9)).isCloseTo(90.0, within(1.0));
    }

    @Test
    void turningIsMeasuredTheShortWayRound() {
        assertThat(PolylineGeometry.bearingDifference(350, 10)).isCloseTo(20.0, within(1e-9));
        assertThat(PolylineGeometry.bearingDifference(10, 350)).isCloseTo(-20.0, within(1e-9));
        assertThat(PolylineGeometry.bearingDifference(0, 90)).isCloseTo(90.0, within(1e-9));
    }

    @Test
    void aVehicleTurningPastNorthDoesNotSpinAllTheWayRound() {
        assertThat(PolylineGeometry.interpolateBearing(350, 10, 0.5)).isCloseTo(0.0, within(1e-9));
        assertThat(PolylineGeometry.interpolateBearing(10, 350, 0.5)).isCloseTo(0.0, within(1e-9));
        assertThat(PolylineGeometry.interpolateBearing(0, 90, 0.5)).isCloseTo(45.0, within(1e-9));
    }

    @Test
    void anEmptyOrSinglePointShapeIsHandledWithoutFailing() {
        assertThat(PolylineGeometry.lengthMetres(List.of())).isZero();
        assertThat(PolylineGeometry.pointAt(List.of(), 0.5)).isNull();
        assertThat(PolylineGeometry.pointAt(List.of(ORIGIN), 0.5)).isEqualTo(ORIGIN);
        assertThat(PolylineGeometry.bearingAt(List.of(ORIGIN), 0.5)).isZero();
        assertThat(PolylineGeometry.lengthMetres(null)).isZero();
    }

    @Test
    void progressOutsideZeroToOneIsClampedToTheEnds() {
        assertThat(PolylineGeometry.pointAt(List.of(ORIGIN, NORTH), -5)).isEqualTo(ORIGIN);
        assertThat(PolylineGeometry.pointAt(List.of(ORIGIN, NORTH), 5)).isEqualTo(NORTH);
    }
}
