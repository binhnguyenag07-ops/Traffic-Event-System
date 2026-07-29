package team.traffic.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.demand.CompletionPolicy;
import team.traffic.domain.demand.PurposeId;
import team.traffic.domain.demand.ResolvedPlace;
import team.traffic.domain.demand.TripLeg;
import team.traffic.domain.demand.TripPlan;
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
import team.traffic.simulation.DemandRunStatistics;

class DemandScheduleDtoTest {

    @Test
    void publishesReadablePlacesDwellAndNextDayReturnTimes() {
        RoadNetwork network = network();
        ResolvedPlace home = ResolvedPlace.building("HOME", "A");
        ResolvedPlace office = ResolvedPlace.building("OFFICE", "B");
        TripPlan plan = new TripPlan(
                "late-commute",
                new PurposeId("COMMUTE"),
                VehicleType.CAR,
                home,
                List.of(
                        new TripLeg(office, 3_600),
                        new TripLeg(home, 0)),
                CompletionPolicy.RETURN_TO_ORIGIN,
                17 * 3_600 + 59 * 60);
        DemandRunStatistics statistics =
                new DemandRunStatistics(1, 0, 1, 0, List.of("rejected-plan"));

        DemandScheduleDto dto =
                DemandScheduleDto.of(List.of(plan), statistics, network, 6 * 3_600);

        assertThat(dto.totalPlans()).isOne();
        assertThat(dto.failedPlans()).isOne();
        assertThat(dto.failedPlanIds()).containsExactly("rejected-plan");
        assertThat(dto.estimateBasis()).isEqualTo(
                "DIRECTED_FREE_FLOW_SHORTEST_TIME_PLUS_BUILDING_DRIVEWAYS_AND_DWELL");

        DemandScheduleDto.DemandPlanDto published = dto.plans().get(0);
        assertThat(published.origin().name()).isEqualTo("Home 42");
        assertThat(published.origin().latitude()).isEqualTo(10.77001);
        assertThat(published.origin().longitude()).isEqualTo(106.70001);
        assertThat(published.origin().accessName()).isEqualTo("Home Street");
        assertThat(published.departure().clock()).isEqualTo("23:59");
        assertThat(published.departure().dayOffset()).isZero();
        assertThat(published.legs()).hasSize(2);
        assertThat(published.legs().get(0).destination().name())
                .isEqualTo("City Hall Annex");
        assertThat(published.legs().get(0).dwellSeconds()).isEqualTo(3_600);
        assertThat(published.legs().get(1).returnsToOrigin()).isTrue();
        assertThat(published.estimatedReturnArrival()).isNotNull();
        assertThat(published.estimatedReturnArrival().dayOffset()).isEqualTo(1);
        assertThat(published.estimatedReturnArrival().clock()).startsWith("01:");
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection(
                "A", "Home Street", 0, 0, LocationType.NORMAL,
                10.77002, 106.70002));
        network.addIntersection(new Intersection(
                "B", "Office Street", 1, 0, LocationType.NORMAL,
                10.77102, 106.70102));
        network.addRoad(road("AB", "A", "B"));
        network.addRoad(road("BA", "B", "A"));
        network.addBuilding(new Building(
                "HOME", "Home 42", BuildingType.RESIDENTIAL,
                0, 0, 10.77001, 106.70001,
                List.of(new BuildingAccessPoint("HOME-A", "A", 10))));
        network.addBuilding(new Building(
                "OFFICE", "City Hall Annex", BuildingType.OFFICE,
                1, 0, 10.77101, 106.70101,
                List.of(new BuildingAccessPoint("OFFICE-B", "B", 10))));
        return network;
    }

    private static Road road(String id, String from, String to) {
        return new Road(
                id,
                from,
                to,
                1,
                60,
                20,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN));
    }
}
