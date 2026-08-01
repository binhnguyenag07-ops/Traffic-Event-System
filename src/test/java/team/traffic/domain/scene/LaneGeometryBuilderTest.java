package team.traffic.domain.scene;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadClass;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.dynamics.RoadDataRoadLayout;
import team.traffic.domain.vehicle.pose.PolylineGeometry;
import team.traffic.domain.vehicle.pose.RoadExtent;

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
    void lanePairingMatchesTheSimulatedContinuationWithoutACartesianExplosion() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(junction("A", 10.000, 106.000));
        network.addIntersection(junction("C", 10.005, 106.000));
        network.addIntersection(junction("B", 10.010, 106.003));
        network.addRoad(new Road("AC", "Approach", "AC", "A", "C", 0.55, 50, 100, 0,
                3, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.000, 106.000), new GeoPoint(10.005, 106.000)),
                RoadClass.PRIMARY));
        network.addRoad(new Road("CB", "Exit", "CB", "C", "B", 0.65, 50, 100, 0,
                2, LANE_WIDTH, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(new GeoPoint(10.005, 106.000), new GeoPoint(10.010, 106.003)),
                RoadClass.PRIMARY));

        List<LaneConnector> connectors = new LaneGeometryBuilder(network).connectorsAt("C");

        assertThat(connectors)
                .extracting(LaneConnector::fromLane, LaneConnector::toLane)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(0, 0),
                        org.assertj.core.groups.Tuple.tuple(1, 1),
                        org.assertj.core.groups.Tuple.tuple(2, 1));
    }

    @Test
    void shortDistrictOneRoadDoesNotCreateEndpointCusps() {
        RoadNetwork network = districtOneShortRoadChain();
        LaneGeometryBuilder builder = new LaneGeometryBuilder(network);

        assertSmoothConnector(
                builder,
                network,
                "N5238614065",
                "W220863461-0F",
                "W541781966-0F");
        assertSmoothConnector(
                builder,
                network,
                "N4603207418",
                "W541781966-0F",
                "W220862996-0F");
    }

    @Test
    void aStraightContinuationRunsStraightAcrossTheJunction() {
        RoadNetwork network = crossroads();
        LaneConnector straight = new LaneGeometryBuilder(network).connectorsAt("C").stream()
                .filter(connector -> connector.fromRoadId().equals("NC"))
                .filter(connector -> connector.toRoadId().equals("CS"))
                .findFirst()
                .orElseThrow();

        // Both directed roads are the same straight street split at C, so the
        // connector across it must be a straight line from one stop line to the
        // other — never a detour to the node and back, which reverses its
        // tangent and spins a rendered vehicle.
        assertEveryStepAdvancesAlongTheChord(straight, "a straight continuation");
        double junctionRadius = new RoadExtent(network, new RoadDataRoadLayout())
                .junctionRadiusMetres("C");
        assertThat(PolylineGeometry.lengthMetres(straight.curve()))
                .as("it should span the junction, both stop lines included")
                .isCloseTo(2 * junctionRadius, within(0.2));
    }

    @Test
    void aRightTurnCurvesForwardsRatherThanDoublingBackOverTheJunction() {
        RoadNetwork network = crossroads();

        // A right turn is the case that used to invert. A lane is the road shape
        // pushed sideways, so the southbound approach ends on one side of the
        // node and the westbound exit begins on the other; joining those two
        // points sent the vehicle backwards through a 180 degree reversal.
        LaneConnector rightTurn = new LaneGeometryBuilder(network).connectorsAt("C").stream()
                .filter(connector -> connector.fromRoadId().equals("NC"))
                .filter(connector -> connector.toRoadId().equals("CW"))
                .findFirst()
                .orElseThrow();

        assertEveryStepAdvancesAlongTheChord(rightTurn, "a right turn");
    }

    /** No segment of a connector may point against its own start-to-end chord. */
    private static void assertEveryStepAdvancesAlongTheChord(LaneConnector connector, String what) {
        List<GeoPoint> curve = connector.curve();
        GeoPoint start = curve.get(0);
        GeoPoint end = curve.get(curve.size() - 1);
        double chordBearing = PolylineGeometry.bearingBetween(start, end);
        for (int index = 1; index < curve.size(); index++) {
            GeoPoint from = curve.get(index - 1);
            GeoPoint to = curve.get(index);
            if (PolylineGeometry.lengthMetres(List.of(from, to)) <= 0) continue;
            double turn = Math.abs(PolylineGeometry.bearingDifference(
                    chordBearing, PolylineGeometry.bearingBetween(from, to)));
            assertThat(turn)
                    .as("%s must never travel against its own chord (segment %d)", what, index)
                    .isLessThan(90.0);
        }
    }

    private static void assertSmoothConnector(
            LaneGeometryBuilder builder,
            RoadNetwork network,
            String junctionId,
            String fromRoadId,
            String toRoadId) {
        LaneConnector connector = builder.connectorsAt(junctionId).stream()
                .filter(candidate -> candidate.fromRoadId().equals(fromRoadId))
                .filter(candidate -> candidate.toRoadId().equals(toRoadId))
                .filter(candidate -> candidate.fromLane() == 0 && candidate.toLane() == 0)
                .findFirst()
                .orElseThrow();
        Road fromRoad = network.getRoad(fromRoadId);
        Road toRoad = network.getRoad(toRoadId);
        RoadExtent extent = new RoadExtent(network, new RoadDataRoadLayout());
        List<GeoPoint> fromLane = builder.lanesOf(fromRoad).get(0).centreline();
        List<GeoPoint> toLane = builder.lanesOf(toRoad).get(0).centreline();
        double expectedStart = PolylineGeometry.bearingAt(
                fromLane, extent.exitFraction(fromRoad));
        double expectedEnd = PolylineGeometry.bearingAt(
                toLane, extent.entryFraction(toRoad));
        List<GeoPoint> curve = connector.curve();

        assertThat(curve).hasSizeGreaterThan(2);
        double first = PolylineGeometry.bearingBetween(curve.get(0), curve.get(1));
        double last = PolylineGeometry.bearingBetween(
                curve.get(curve.size() - 2), curve.get(curve.size() - 1));
        assertThat(Math.abs(PolylineGeometry.bearingDifference(expectedStart, first)))
                .as("%s starts tangent to its incoming lane", fromRoadId + " -> " + toRoadId)
                .isLessThanOrEqualTo(45.0);
        assertThat(Math.abs(PolylineGeometry.bearingDifference(last, expectedEnd)))
                .as("%s ends tangent to its outgoing lane", fromRoadId + " -> " + toRoadId)
                .isLessThanOrEqualTo(45.0);
        for (int index = 2; index < curve.size(); index++) {
            double previous = PolylineGeometry.bearingBetween(
                    curve.get(index - 2), curve.get(index - 1));
            double current = PolylineGeometry.bearingBetween(
                    curve.get(index - 1), curve.get(index));
            assertThat(Math.abs(PolylineGeometry.bearingDifference(previous, current)))
                    .as("no sampled connector step forms a cusp")
                    .isLessThanOrEqualTo(45.0);
        }
    }

    @Test
    void aRealTurnRetainsACurvedConnector() {
        RoadNetwork network = crossroads();
        LaneConnector turn = new LaneGeometryBuilder(network).connectorsAt("C").stream()
                .filter(connector -> connector.fromRoadId().equals("NC"))
                .filter(connector -> connector.toRoadId().equals("CE"))
                .findFirst()
                .orElseThrow();

        assertThat(turn.curve())
                .as("preventing loops must not flatten legitimate turns")
                .hasSizeGreaterThan(2);
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
        planar.addIntersection(new Intersection("A", "A", 0, 0));
        planar.addIntersection(new Intersection("B", "B", 1, 0));
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

    private RoadNetwork districtOneShortRoadChain() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(junction("N411925307", 10.7720824, 106.6923274));
        network.addIntersection(junction("N5238614065", 10.7727238, 106.6911548));
        network.addIntersection(junction("N4603207418", 10.7726603, 106.6911167));
        network.addIntersection(junction("N4610625918", 10.7714596, 106.6931043));
        network.addRoad(new Road(
                "W220863461-0F", "Cách Mạng Tháng Tám", "W220863461-0",
                "N411925307", "N5238614065", 0.1466, 50, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(
                        new GeoPoint(10.7720824, 106.6923274),
                        new GeoPoint(10.7721554, 106.6921940),
                        new GeoPoint(10.7723496, 106.6918389),
                        new GeoPoint(10.7724827, 106.6915956),
                        new GeoPoint(10.7726749, 106.6912442),
                        new GeoPoint(10.7727238, 106.6911548)),
                RoadClass.PRIMARY));
        network.addRoad(new Road(
                "W541781966-0F", "Bùi Thị Xuân", "W541781966-0",
                "N5238614065", "N4603207418", 0.01, 40, 65, 0,
                2, 3.25, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(
                        new GeoPoint(10.7727238, 106.6911548),
                        new GeoPoint(10.7726603, 106.6911167)),
                RoadClass.TERTIARY));
        network.addRoad(new Road(
                "W220862996-0F", "Cách Mạng Tháng Tám", "W220862996-0",
                "N4603207418", "N4610625918", 0.2572, 50, 100, 0,
                2, 3.5, new RoadCondition(0, 0, RoadStatus.OPEN),
                List.of(
                        new GeoPoint(10.7726603, 106.6911167),
                        new GeoPoint(10.7726148, 106.6912073),
                        new GeoPoint(10.7724198, 106.6915434),
                        new GeoPoint(10.7720756, 106.6921617),
                        new GeoPoint(10.7720507, 106.6922064),
                        new GeoPoint(10.7716121, 106.6929900),
                        new GeoPoint(10.7715430, 106.6930711),
                        new GeoPoint(10.7714596, 106.6931043)),
                RoadClass.PRIMARY));
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
        return new Intersection(id, id, lon, lat, lat, lon);
    }
}
