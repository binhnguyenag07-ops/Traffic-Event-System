package team.traffic.domain.demand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;
import team.traffic.domain.vehicle.VehicleType;

class TripScheduleEstimatorTest {

    @Test
    void usesDirectedFreeFlowShortestTimeThenAddsBothDrivewaysAndDwell() {
        RoadNetwork network = network();
        ResolvedPlace home = ResolvedPlace.building("HOME", "A");
        ResolvedPlace work = ResolvedPlace.building("WORK", "C");
        TripPlan plan = new TripPlan(
                "commute",
                new PurposeId("COMMUTE"),
                VehicleType.MOTORCYCLE,
                home,
                List.of(
                        new TripLeg(work, 3_600),
                        new TripLeg(home, 0)),
                CompletionPolicy.RETURN_TO_ORIGIN,
                23 * 3_600 + 59 * 60);

        TripScheduleEstimator.Estimate estimate =
                new TripScheduleEstimator(network).estimate(plan);

        // A->B->C is 120 s and beats the slower direct A->C edge (360 s).
        // Each leg also includes 15 m / 15 km/h + 25 m / 15 km/h = 9.6 s.
        assertThat(estimate.legs()).hasSize(2);
        assertThat(estimate.legs().get(0).travelSeconds()).isEqualTo(129.6);
        assertThat(estimate.legs().get(0).arrivalSeconds())
                .isEqualTo(23 * 3_600 + 59 * 60 + 129.6);
        assertThat(estimate.legs().get(0).nextDepartureSeconds())
                .isEqualTo(23 * 3_600 + 59 * 60 + 129.6 + 3_600);
        assertThat(estimate.legs().get(1).travelSeconds()).isEqualTo(129.6);
        assertThat(estimate.finalArrivalSeconds())
                .isEqualTo(23 * 3_600 + 59 * 60 + 129.6 + 3_600 + 129.6);
    }

    @Test
    void failsClearlyWhenOnlyTheReverseDirectionExists() {
        RoadNetwork network = new RoadNetwork();
        addIntersection(network, "A");
        addIntersection(network, "B");
        network.addRoad(road("BA", "B", "A", 1, 60));
        TripPlan plan = new TripPlan(
                "unreachable",
                new PurposeId("ONE_WAY"),
                VehicleType.CAR,
                ResolvedPlace.intersection("A"),
                List.of(new TripLeg(ResolvedPlace.intersection("B"), 0)),
                CompletionPolicy.REMOVE_AT_LAST_STOP,
                0);

        assertThatThrownBy(() -> new TripScheduleEstimator(network).estimate(plan))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("A -> B");
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        addIntersection(network, "A");
        addIntersection(network, "B");
        addIntersection(network, "C");
        network.addRoad(road("AB", "A", "B", 1, 60));
        network.addRoad(road("BC", "B", "C", 1, 60));
        network.addRoad(road("AC-SLOW", "A", "C", 1, 10));
        network.addRoad(road("CB", "C", "B", 1, 60));
        network.addRoad(road("BA", "B", "A", 1, 60));
        network.addBuilding(new Building(
                "HOME", "Home", BuildingType.RESIDENTIAL,
                0, 0, null, null,
                List.of(new BuildingAccessPoint("HOME-A", "A", 15))));
        network.addBuilding(new Building(
                "WORK", "Work", BuildingType.OFFICE,
                2, 0, null, null,
                List.of(new BuildingAccessPoint("WORK-C", "C", 25))));
        return network;
    }

    private static void addIntersection(RoadNetwork network, String id) {
        network.addIntersection(new Intersection(
                id, id + " junction", 0, 0, LocationType.NORMAL));
    }

    private static Road road(
            String id, String from, String to, double distanceKm, double speedKph) {
        return new Road(
                id,
                from,
                to,
                distanceKm,
                speedKph,
                20,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
