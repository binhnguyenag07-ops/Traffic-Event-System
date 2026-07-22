package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class BuildingTest {
    @Test
    void keepsMultipleValidatedAccessPoints() {
        Building building = new Building(
                "B1", "Apartment", BuildingType.RESIDENTIAL, 1.0, 2.0,
                10.77, 106.70,
                List.of(
                        new BuildingAccessPoint("B1-W", "ACCESS-W", 12.0),
                        new BuildingAccessPoint("B1-E", "ACCESS-E", 8.0)));

        assertThat(building.getAccessPoints())
                .extracting(BuildingAccessPoint::intersectionId)
                .containsExactly("ACCESS-W", "ACCESS-E");
    }

    @Test
    void rejectsDuplicateAccessIds() {
        assertThatThrownBy(() -> new Building(
                "B1", "Apartment", BuildingType.RESIDENTIAL, 1.0, 2.0,
                null, null,
                List.of(
                        new BuildingAccessPoint("ENTRY", "A", 0),
                        new BuildingAccessPoint("ENTRY", "B", 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate building access id");
    }
}
