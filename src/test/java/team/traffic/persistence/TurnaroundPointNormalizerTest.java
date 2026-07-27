package team.traffic.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.MovementProvenance;
import team.traffic.domain.scene.LaneGeometryBuilder;
import team.traffic.domain.vehicle.dynamics.TurnDirection;

class TurnaroundPointNormalizerTest {
    @Test
    void explicitMidBlockPointBecomesJunctionSegmentsAndSharedConnectors()
            throws Exception {
        String json = """
                {
                  "intersections": [
                    {"id":"A","name":"A","x":0,"y":0,"locationType":"NORMAL",
                     "latitude":10.0,"longitude":106.0},
                    {"id":"B","name":"B","x":100,"y":0,"locationType":"NORMAL",
                     "latitude":10.0,"longitude":106.01}
                  ],
                  "roads": [
                    {
                      "id":"AB","name":"Main","physicalRoadId":"main",
                      "startIntersectionId":"A","endIntersectionId":"B",
                      "distance":1.0,"speedLimit":40,"capacity":100,
                      "currentVehicleCount":0,"laneCount":1,"laneWidthMetres":3.5,
                      "congestionLevel":0,"vehicleDensity":0,"roadStatus":"OPEN",
                      "roadClass":"PRIMARY",
                      "geometry":[[10.0,106.0],[10.0,106.01]]
                    },
                    {
                      "id":"BA","name":"Main","physicalRoadId":"main",
                      "startIntersectionId":"B","endIntersectionId":"A",
                      "distance":1.0,"speedLimit":40,"capacity":100,
                      "currentVehicleCount":0,"laneCount":1,"laneWidthMetres":3.5,
                      "congestionLevel":0,"vehicleDensity":0,"roadStatus":"OPEN",
                      "roadClass":"PRIMARY",
                      "geometry":[[10.0,106.01],[10.0,106.0]]
                    }
                  ],
                  "turnaroundPoints": [{
                    "id":"T","physicalRoadId":"main","referenceRoadId":"AB",
                    "fractionAlongReferenceRoad":0.5,"penaltySeconds":20
                  }]
                }
                """;

        var network = new JsonMapLoader().load(new ByteArrayInputStream(
                json.getBytes(StandardCharsets.UTF_8)));

        assertThat(network.getIntersection("T")).isNotNull();
        assertThat(network.getRoad("AB")).isNull();
        assertThat(network.getRoad("BA")).isNull();
        assertThat(network.getRoadCount()).isEqualTo(4);
        assertThat(network.getMovementGraph().explicitMovements())
                .hasSize(2)
                .allSatisfy(movement -> {
                    assertThat(movement.direction()).isEqualTo(TurnDirection.U_TURN);
                    assertThat(movement.provenance()).isEqualTo(MovementProvenance.EXPLICIT);
                });
        assertThat(new LaneGeometryBuilder(network).connectorsAt("T"))
                .hasSizeGreaterThanOrEqualTo(2);
    }
}
