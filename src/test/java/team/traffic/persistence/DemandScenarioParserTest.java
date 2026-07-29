package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Intersection;
import team.traffic.domain.network.LocationType;
import team.traffic.domain.network.RoadNetwork;

class DemandScenarioParserTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DemandScenarioParser parser = new DemandScenarioParser();

    @Test
    void rejectsAProductionObjectThatMixesBothUnitSystems() throws Exception {
        String json = validDemand().replace(
                "\"vehiclesPerDay\": 10",
                "\"vehiclesPerDay\": 10, \"tripsPerPersonPerDay\": 2");

        assertThatThrownBy(() -> parser.parseOptional(
                objectMapper.readTree(json), network(), Path.of(".")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not declare tripsPerPersonPerDay");
    }

    @Test
    void rejectsAnExplicitPlaceWithTwoCompetingIdentifiers() throws Exception {
        String json = validDemand().replace(
                "\"intersectionId\": \"B\"",
                "\"intersectionId\": \"B\", \"buildingId\": \"also-B\"");

        assertThatThrownBy(() -> parser.parseOptional(
                objectMapper.readTree(json), network(), Path.of(".")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one buildingId or intersectionId");
    }

    @Test
    void rejectsAFlowWithoutAnExplicitCompletionPolicy() throws Exception {
        String json = validDemand().replace(
                "\"completion\": \"REMOVE_AT_LAST_STOP\",",
                "");

        assertThatThrownBy(() -> parser.parseOptional(
                objectMapper.readTree(json), network(), Path.of(".")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("completion is required");
    }

    @Test
    void warmUpDefaultsToZeroAndAcceptsABoundedPreRoll() throws Exception {
        var withoutWarmUp = parser.parseOptional(
                objectMapper.readTree(validDemand()), network(), Path.of("."))
                .orElseThrow();
        var withWarmUp = parser.parseOptional(
                objectMapper.readTree(validDemand().replace(
                        "\"scale\": 1,",
                        "\"scale\": 1, \"warmUpSeconds\": 300,")),
                network(),
                Path.of("."))
                .orElseThrow();

        assertThat(withoutWarmUp.warmUpSeconds()).isZero();
        assertThat(withWarmUp.warmUpSeconds()).isEqualTo(300);
    }

    @Test
    void automaticCollisionEventsDefaultOnAndCanBeDisabledExplicitly() throws Exception {
        var defaultPolicy = parser.parseOptional(
                objectMapper.readTree(validDemand()), network(), Path.of("."))
                .orElseThrow();
        var disabled = parser.parseOptional(
                objectMapper.readTree(validDemand().replace(
                        "\"scale\": 1,",
                        "\"scale\": 1, \"automaticCollisionEvents\": false,")),
                network(),
                Path.of("."))
                .orElseThrow();

        assertThat(defaultPolicy.automaticCollisionEvents()).isTrue();
        assertThat(disabled.automaticCollisionEvents()).isFalse();
    }

    @Test
    void rejectsWarmUpThatConsumesTheWholeScenario() throws Exception {
        String json = validDemand().replace(
                "\"scale\": 1,",
                "\"scale\": 1, \"warmUpSeconds\": 3600,");

        assertThatThrownBy(() -> parser.parseOptional(
                objectMapper.readTree(json), network(), Path.of(".")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warm-up");
    }

    private static RoadNetwork network() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(new Intersection("A", "A", 0, 0, LocationType.NORMAL));
        network.addIntersection(new Intersection("B", "B", 1, 0, LocationType.NORMAL));
        return network;
    }

    private static String validDemand() {
        return """
                {
                  "demandParameters": "data/demand/default_assumptions.json",
                  "startTimeOfDay": "06:00",
                  "durationHours": 1,
                  "seed": 1,
                  "scale": 1,
                  "demandSources": [{
                    "id": "source",
                    "from": {"kind": "EXPLICIT", "intersectionId": "A"},
                    "production": {"kind": "ABSOLUTE", "vehiclesPerDay": 10},
                    "flows": [{
                      "id": "flow",
                      "purpose": "TEST_FLOW",
                      "share": 1,
                      "vehicleMix": [{"type": "CAR", "share": 1}],
                      "curve": "FLAT",
                      "completion": "REMOVE_AT_LAST_STOP",
                      "legs": [{
                        "to": {"kind": "EXPLICIT", "intersectionId": "B"}
                      }]
                    }]
                  }]
                }
                """;
    }
}
