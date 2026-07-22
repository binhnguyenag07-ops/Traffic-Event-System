package team.traffic.domain.vehicle.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import team.traffic.domain.vehicle.VehicleType;

class DriverProfileVariationTest {
    @Test
    void motorcycleProfileUsesCompactGapsAndLowerLaneChangePoliteness() {
        DriverProfile car = DriverProfile.forType(VehicleType.CAR);
        DriverProfile motorcycle = DriverProfile.forType(VehicleType.MOTORCYCLE);

        assertThat(motorcycle.minimumGapMetres()).isLessThan(car.minimumGapMetres());
        assertThat(motorcycle.reactionTimeSeconds()).isLessThan(car.reactionTimeSeconds());
        assertThat(motorcycle.politeness()).isLessThan(car.politeness());
        assertThat(motorcycle.lateralAccelerationMps2()).isGreaterThan(car.lateralAccelerationMps2());
    }

    @Test
    void variationIsDifferentAcrossVehiclesAndStableForOneId() {
        DriverProfile first = DriverProfile.variedFor(VehicleType.CAR, "car-a");
        DriverProfile repeated = DriverProfile.variedFor(VehicleType.CAR, "car-a");
        DriverProfile second = DriverProfile.variedFor(VehicleType.CAR, "car-b");

        assertThat(repeated).isEqualTo(first);
        assertThat(second).isNotEqualTo(first);
        assertThat(first.minimumGapMetres()).isBetween(2.5 * 0.85, 2.5 * 1.15);
    }
}
