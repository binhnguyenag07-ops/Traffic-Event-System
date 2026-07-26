package team.traffic.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

/**
 * The clock is what lets demand know whether traffic should be going to work or
 * coming home, so the arithmetic around midnight and the derived-not-stored rule
 * both matter more than they look.
 */
class SimulationClockTest {
    private static final double SIX_AM = 6 * 3600;
    private static final double ELEVEN_PM = 23 * 3600;

    @Test
    void startsAtSixInTheMorningByDefault() {
        SimulationClock clock = new SimulationClock();

        assertThat(clock.getTimeOfDaySeconds()).isEqualTo(SIX_AM);
        assertThat(clock.getHourOfDay()).isEqualTo(6);
        assertThat(clock.formatTimeOfDay()).isEqualTo("06:00");
    }

    @Test
    void advancesTheClockAsTheSimulationRuns() {
        SimulationClock clock = new SimulationClock(SIX_AM);

        clock.tick(3600);
        clock.tick(1800);

        assertThat(clock.getCurrentTick()).isEqualTo(2);
        assertThat(clock.getElapsedSeconds()).isEqualTo(5400);
        assertThat(clock.formatTimeOfDay()).isEqualTo("07:30");
        assertThat(clock.getHourOfDay()).isEqualTo(7);
    }

    @Test
    void wrapsPastMidnightRatherThanRunningTo25OClock() {
        SimulationClock clock = new SimulationClock(ELEVEN_PM);

        clock.tick(2 * 3600);

        assertThat(clock.formatTimeOfDay()).isEqualTo("01:00");
        assertThat(clock.getHourOfDay()).isOne();
        assertThat(clock.getDaysElapsed())
                .as("one midnight crossed")
                .isEqualTo(1);
    }

    @Test
    void normalisesAStartTimeOutsideOneDay() {
        assertThat(new SimulationClock(SimulationClock.SECONDS_PER_DAY + SIX_AM)
                .getStartTimeOfDaySeconds()).isEqualTo(SIX_AM);
        assertThat(new SimulationClock(-3600).formatTimeOfDay())
                .as("an hour before midnight is 23:00, not a negative time")
                .isEqualTo("23:00");
    }

    @Test
    void labelsAnyElapsedReadingWithoutTheFrameCarryingAClock() {
        SimulationClock clock = new SimulationClock(SIX_AM);
        clock.tick(7200);

        // A recorded frame keeps only its elapsed seconds; the time of day it
        // represents is worked out from that, so the two cannot disagree.
        assertThat(clock.timeOfDayAt(3600)).isCloseTo(7 * 3600, within(1e-9));
        assertThat(SimulationClock.timeOfDayAt(SIX_AM, 3600)).isCloseTo(7 * 3600, within(1e-9));
    }

    @Test
    void resetReturnsToTheStartTimeNotToMidnight() {
        SimulationClock clock = new SimulationClock(SIX_AM);
        clock.tick(5 * 3600);

        clock.reset();

        assertThat(clock.getCurrentTick()).isZero();
        assertThat(clock.getElapsedSeconds()).isZero();
        assertThat(clock.formatTimeOfDay())
                .as("a reset run begins its day again, it does not jump to 00:00")
                .isEqualTo("06:00");
    }

    @Test
    void formatsWithLeadingZeroes() {
        assertThat(SimulationClock.formatTimeOfDay(0)).isEqualTo("00:00");
        assertThat(SimulationClock.formatTimeOfDay(9 * 3600 + 5 * 60)).isEqualTo("09:05");
        assertThat(SimulationClock.formatTimeOfDay(17 * 3600 + 45 * 60)).isEqualTo("17:45");
    }

    @Test
    void rejectsANonPositiveStepAndANegativeElapsedReading() {
        SimulationClock clock = new SimulationClock();

        assertThatThrownBy(() -> clock.tick(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be positive");
        assertThatThrownBy(() -> clock.timeOfDayAt(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
