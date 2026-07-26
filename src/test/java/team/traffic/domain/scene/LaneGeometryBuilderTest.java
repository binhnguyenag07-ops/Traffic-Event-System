package team.traffic.domain.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.pose.PolylineGeometry;

/**
 * Lane geometry is only useful if a lane ends up where its traffic runs, so
 * these tests measure the distances between the lines produced rather than just
 * counting them.
 */
class LaneGeometryBuilderTest {
    private static final double LANE_WIDTH = 3.5;

    @Test
    void aTwoLaneRoadYieldsTwoCentrelinesOneLaneWidthApart() {
        RoadNetwork network = eastWestNetwork(2);
        LaneGeometryBuilder builder = new LaneGeometryBuilder(network);

        List<LaneGeometry> lanes = builder.lanesOf(network.getRoad("AB"));

        assertThat(lanes).hasSize(2);
        double apart = PolylineGeometry.lengthMetres(List.of(
                lanes.get(0).centreline().get(0), lanes.get(1).centreline().get(0)));
        assertThat(apart)
                .as("adjacent lane centres sit one lane width apart")
                .isCloseTo(LANE_WIDTH, within(0.05));
    }

    @Test
    void aSingleLaneRoadKeepsItsCentrelineOnTheRoadCentreline() {
        RoadNetwork network = eastWestNetwork(1);
        LaneGeometryBuilder builder = new LaneGeometryBuilder(network);

        List<LaneGeometry> lanes = builder.lanesOf(network.getRoad("AB"));

        assertThat(lanes).hasSize(1);
        // Road runs due east along a line of constant latitude; a centred lane
        // must not move off it.
        assertThat(lanes.get(0).centreline().get(0).latitude())
                .isCloseTo(10.0, within(1e-9));
    }

    @Test
    void lanesStayOneWidthApartAlongACurvedRoad() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(junction("A", 10.000, 106.000));
        network.addIntersection(junction("B", 10.010, 106.010));
        network.addRoad(new Road("AB", "Curve", "AB", "A", "B", 1.6, 50, 100, 0,
                2, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.000, 106.000),
                        new GeoPoint(10.004, 106.007),
                        new GeoPoint(10.010, 106.010)),
                RoadClass.SECONDARY));

        List<LaneGeometry> lanes = new LaneGeometryBuilder(network).lanesOf(network.getRoad("AB"));

        // Every sample point of one lane is a lane width from the matching point
        // of the other, all the way round the bend.
        List<GeoPoint> left = lanes.get(0).centreline();
        List<GeoPoint> right = lanes.get(1).centreline();
        assertThat(left).hasSameSizeAs(right);
        for (int index = 0; index < left.size(); index++) {
            double apart = PolylineGeometry.lengthMetres(List.of(left.get(index), right.get(index)));
            assertThat(apart).isCloseTo(LANE_WIDTH, within(0.15));
        }
    }

    @Test
    void aStopLineSpansTheCarriageway() {
        RoadNetwork network = eastWestNetwork(2);
        LaneGeometryBuilder builder = new LaneGeometryBuilder(network);

        StopLine stopLine = builder.stopLineOf(network.getRoad("AB"));

        double width = PolylineGeometry.lengthMetres(List.of(stopLine.from(), stopLine.to()));
        assertThat(width)
                .as("a two-lane road's stop line is two lanes wide")
                .isCloseTo(2 * LANE_WIDTH, within(0.1));
    }

    @Test
    void aOneWayRoadIsMarkedOneWayAndATwoWayPairIsNot() {
        RoadNetwork oneWay = eastWestNetwork(1);
        assertThat(new LaneGeometryBuilder(oneWay).lanesOf(oneWay.getRoad("AB")).get(0).oneway())
                .isTrue();

        RoadNetwork twoWay = eastWestNetwork(1);
        twoWay.addRoad(new Road("BA", "Back", "AB", "B", "A", 1.1, 50, 100, 0,
                1, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.0, 106.010), new GeoPoint(10.0, 106.000)),
                RoadClass.SECONDARY));
        assertThat(new LaneGeometryBuilder(twoWay).lanesOf(twoWay.getRoad("AB")).get(0).oneway())
                .as("a road with a reverse twin is not one-way")
                .isFalse();
    }

    @Test
    void oppositeDirectionsOfOneStreetDoNotShareTheSameTarmac() {
        RoadNetwork twoWay = eastWestNetwork(1);
        twoWay.addRoad(new Road("BA", "Back", "AB", "B", "A", 1.1, 50, 100, 0,
                1, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.0, 106.010), new GeoPoint(10.0, 106.000)),
                RoadClass.SECONDARY));
        LaneGeometryBuilder builder = new LaneGeometryBuilder(twoWay);

        GeoPoint forward = builder.lanesOf(twoWay.getRoad("AB")).get(0).centreline().get(0);
        GeoPoint back = builder.lanesOf(twoWay.getRoad("BA")).get(0).centreline().get(1);

        // Both directions run over one line of geometry. Centred, their traffic
        // would be drawn on top of itself; each side takes its own carriageway.
        double apart = PolylineGeometry.lengthMetres(List.of(forward, back));
        assertThat(apart).isCloseTo(LANE_WIDTH, within(0.1));
    }

    @Test
    void aOneWayRoadKeepsTheCentrelineBecauseNothingOpposesIt() {
        RoadNetwork oneWay = eastWestNetwork(1);

        GeoPoint only = new LaneGeometryBuilder(oneWay)
                .lanesOf(oneWay.getRoad("AB")).get(0).centreline().get(0);

        assertThat(only.latitude()).isCloseTo(10.0, within(1e-9));
    }

    @Test
    void aFourWayJunctionConnectsEveryApproachToEveryExitButTheUTurn() {
        RoadNetwork network = crossroads();
        LaneGeometryBuilder builder = new LaneGeometryBuilder(network);

        List<LaneConnector> connectors = builder.connectorsAt("C");

        // Four roads in, four out; each of the four arrivals reaches the three
        // exits that are not the road it came in on. One lane each.
        assertThat(connectors).hasSize(4 * 3);
        assertThat(connectors).allSatisfy(connector ->
                assertThat(connector.curve().size()).isGreaterThanOrEqualTo(2));
    }

    @Test
    void aJunctionPavementEnclosesAnArea() {
        RoadNetwork network = crossroads();

        JunctionGeometry pavement = new LaneGeometryBuilder(network).junctionGeometryOf("C");

        assertThat(pavement.hasArea()).isTrue();
        assertThat(pavement.pavement().size()).isGreaterThanOrEqualTo(4);
    }

    @Test
    void aJunctionPavementUsesTheNearEndOfOutgoingRoads() {
        RoadNetwork network = crossroads();
        GeoPoint centre = new GeoPoint(10.000, 106.000);

        JunctionGeometry pavement = new LaneGeometryBuilder(network).junctionGeometryOf("C");

        assertThat(pavement.pavement()).allSatisfy(point -> {
            double distance = PolylineGeometry.lengthMetres(List.of(centre, point));
            assertThat(distance)
                    .as("a pavement corner stays beside its junction")
                    .isLessThan(10.0);
        });
    }

    @Test
    void aScenarioWithoutCoordinatesYieldsNoGeometry() {
        RoadNetwork planar = new RoadNetwork();
        planar.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        planar.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        planar.addRoad(new Road("AB", "A", "B", 1.0, 50, 20, 0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));

        LaneGeometryBuilder builder = new LaneGeometryBuilder(planar);

        assertThat(builder.lanesOf(planar.getRoad("AB"))).isEmpty();
        assertThat(builder.stopLineOf(planar.getRoad("AB"))).isNull();
    }

    private RoadNetwork eastWestNetwork(int laneCount) {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(junction("A", 10.0, 106.000));
        network.addIntersection(junction("B", 10.0, 106.010));
        network.addRoad(new Road("AB", "Main", "AB", "A", "B", 1.1, 50, 100, 0,
                laneCount, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.0, 106.000), new GeoPoint(10.0, 106.010)),
                RoadClass.SECONDARY));
        return network;
    }

    private RoadNetwork crossroads() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(junction("C", 10.000, 106.000));
        network.addIntersection(junction("N", 10.010, 106.000));
        network.addIntersection(junction("S", 9.990, 106.000));
        network.addIntersection(junction("E", 10.000, 106.010));
        network.addIntersection(junction("W", 10.000, 105.990));
        addTwoWay(network, "N", 10.010, 106.000);
        addTwoWay(network, "S", 9.990, 106.000);
        addTwoWay(network, "E", 10.000, 106.010);
        addTwoWay(network, "W", 10.000, 105.990);
        return network;
    }

    private void addTwoWay(RoadNetwork network, String arm, double lat, double lon) {
        network.addRoad(new Road(arm + "C", "Arm", arm + "C", arm, "C", 1.1, 50, 100, 0,
                1, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(lat, lon), new GeoPoint(10.000, 106.000)),
                RoadClass.SECONDARY));
        network.addRoad(new Road("C" + arm, "Arm", arm + "C", "C", arm, 1.1, 50, 100, 0,
                1, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.000, 106.000), new GeoPoint(lat, lon)),
                RoadClass.SECONDARY));
    }

    private Intersection junction(String id, double lat, double lon) {
        // The five-arg constructor leaves latitude and longitude null; the scene
        // builder needs the geographic pair, so set it explicitly.
        return new Intersection(id, id, lon, lat, LocationType.NORMAL, lat, lon);
    }
}
