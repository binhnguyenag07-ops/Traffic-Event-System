package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import team.traffic.domain.demand.GravityDemandModel;

class JsonScenarioLoaderTest {
    @Test
    void loadsTheShippedMapDemandEventsAndScriptedVehicleSectionsTogether() throws Exception {
        JsonScenarioLoader.LoadedScenario loaded = new JsonScenarioLoader().load(
                Path.of("data/scenarios/district1_typical_day.json"));

        assertThat(loaded.id()).isEqualTo("district-1");
        assertThat(loaded.network().getIntersectionCount()).isGreaterThan(100);
        assertThat(loaded.network().getBuildingCount()).isEqualTo(1162);
        assertThat(loaded.demand()).isPresent();
        assertThat(loaded.demand().orElseThrow().sources()).hasSize(2);
        assertThat(loaded.vehicles()).isEmpty();
        assertThat(loaded.events()).isEmpty();

        GravityDemandModel first =
                new GravityDemandModel(loaded.network(), loaded.demand().orElseThrow());
        GravityDemandModel replay =
                new GravityDemandModel(loaded.network(), loaded.demand().orElseThrow());
        assertThat(first.allPlans()).isNotEmpty().isEqualTo(replay.allPlans());
        assertThat(first.allPlans())
                .anySatisfy(plan -> assertThat(plan.purpose().value()).isEqualTo("COMMUTE"))
                .anySatisfy(plan -> assertThat(plan.purpose().value())
                        .isNotIn("COMMUTE", "ERRAND"));
    }
}
