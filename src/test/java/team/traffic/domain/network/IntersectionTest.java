package team.traffic.domain.network;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class IntersectionTest {

    @Test
    void createsIntersectionWithValidData() {
        Intersection intersection = new Intersection(
                "I1",
                "Campus Gate",
                -10.5,
                20.25,
                LocationType.NORMAL);

        assertThat(intersection.getId()).isEqualTo("I1");
        assertThat(intersection.getName()).isEqualTo("Campus Gate");
        assertThat(intersection.getX()).isEqualTo(-10.5);
        assertThat(intersection.getY()).isEqualTo(20.25);
        assertThat(intersection.getLocationType()).isEqualTo(LocationType.NORMAL);
    }

    @Test
    void rejectsMissingIdentityData() {
        assertThatThrownBy(() -> new Intersection(null, "Name", 0, 0, LocationType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intersection id is required");

        assertThatThrownBy(() -> new Intersection(" ", "Name", 0, 0, LocationType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intersection id is required");

        assertThatThrownBy(() -> new Intersection("I1", " ", 0, 0, LocationType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intersection name is required");
    }

    @Test
    void rejectsNonFiniteCoordinates() {
        assertThatThrownBy(() -> new Intersection(
                "I1", "Name", Double.NaN, 0, LocationType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intersection coordinates must be finite");

        assertThatThrownBy(() -> new Intersection(
                "I1", "Name", 0, Double.POSITIVE_INFINITY, LocationType.NORMAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Intersection coordinates must be finite");
    }

    @Test
    void rejectsMissingLocationType() {
        assertThatThrownBy(() -> new Intersection("I1", "Name", 0, 0, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Location type is required");
    }
}
