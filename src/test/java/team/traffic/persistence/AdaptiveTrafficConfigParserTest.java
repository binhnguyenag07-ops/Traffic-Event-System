package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.UTurnNetworkPolicy;
import team.traffic.simulation.AdaptiveTrafficConfig;

class AdaptiveTrafficConfigParserTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesEveryConfiguredUnitAndPolicy() throws Exception {
        AdaptiveTrafficConfig config = AdaptiveTrafficConfigParser.parse(
                mapper.readTree("""
                        {
                          "enabled": true,
                          "strategicCadenceSeconds": 3,
                          "routing": {
                            "maximumLookaheadRoads": 12,
                            "knowledgeCacheSeconds": 7,
                            "retryBackoffSeconds": [2, 4, 8]
                          },
                          "congestion": {
                            "travelTimeEmaHalfLifeSeconds": 20
                          },
                          "uTurns": {
                            "networkPolicy": "EXPLICIT_ONLY",
                            "defaultPenaltySeconds": 41
                          },
                          "deadlock": {
                            "scanIntervalSeconds": 4,
                            "confirmSeconds": 18,
                            "minimumProgressMetres": 1.5,
                            "reservationSeconds": 6,
                            "assistedRecovery": "DISABLED"
                          }
                        }
                        """));

        assertThat(config.strategicCadenceSeconds()).isEqualTo(3);
        assertThat(config.routing().maximumLookaheadRoads()).isEqualTo(12);
        assertThat(config.routing().knowledgeCacheSeconds()).isEqualTo(7);
        assertThat(config.routing().retryBackoffSeconds())
                .containsExactly(2.0, 4.0, 8.0);
        assertThat(config.congestion().travelTimeEmaHalfLifeSeconds())
                .isEqualTo(20);
        assertThat(config.uTurns().networkPolicy())
                .isEqualTo(UTurnNetworkPolicy.EXPLICIT_ONLY);
        assertThat(config.uTurns().defaultPenaltySeconds()).isEqualTo(41);
        assertThat(config.deadlock().minimumProgressMetres()).isEqualTo(1.5);
    }

    @Test
    void rejectsUnsupportedAssistedRecoveryInsteadOfIgnoringIt() throws Exception {
        var node = mapper.readTree("""
                {"deadlock":{"assistedRecovery":"ENABLED"}}
                """);

        assertThatThrownBy(() -> AdaptiveTrafficConfigParser.parse(node))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not implemented");
    }

    @Test
    void disabledCompatibilityModeDoesNotInferNewUTurns() {
        assertThat(AdaptiveTrafficConfig.disabled().uTurns().networkPolicy())
                .isEqualTo(UTurnNetworkPolicy.EXPLICIT_ONLY);
    }
}
