package team.traffic.api.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import team.traffic.domain.demand.PlaceKind;
import team.traffic.domain.network.GeoPoint;
import team.traffic.domain.scene.PhysicalSceneState;
import team.traffic.domain.scene.SceneVehicle;
import team.traffic.domain.vehicle.JourneyPhase;

class PhysicalViewStateDtoTest {

    @Test
    void statePollCarriesTheSameDrivewayContractAsTheFullScene() {
        SceneVehicle vehicle = new SceneVehicle(
                "V-1", "MOTORCYCLE", 10.0, 106.0, 90.0, 0.0,
                2.1, 0.75, 0, 0.0,
                null, 0.0, false, null, null,
                JourneyPhase.DEPARTING_ORIGIN,
                PlaceKind.BUILDING, "HOME", "HOME-ACCESS",
                PlaceKind.BUILDING, "WORK", "WORK-ACCESS");
        PhysicalSceneState state = new PhysicalSceneState(
                new GeoPoint(10.0, 106.0), 200, List.of(), List.of(vehicle));

        PhysicalViewStateDto.VehicleDto dto =
                PhysicalViewStateDto.of(state, "timeline", 4, 12.0, 250)
                        .vehicles().get(0);

        assertThat(dto.journeyPhase()).isEqualTo("DEPARTING_ORIGIN");
        assertThat(dto.originPlaceKind()).isEqualTo("BUILDING");
        assertThat(dto.originPlaceId()).isEqualTo("HOME");
        assertThat(dto.originAccessIntersectionId()).isEqualTo("HOME-ACCESS");
        assertThat(dto.destinationPlaceId()).isEqualTo("WORK");
        assertThat(dto.destinationAccessIntersectionId()).isEqualTo("WORK-ACCESS");
    }
}
