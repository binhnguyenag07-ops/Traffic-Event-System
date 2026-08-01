package team.traffic.domain.demand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.Building;
import team.traffic.domain.network.BuildingAccessPoint;
import team.traffic.domain.network.BuildingType;
import team.traffic.domain.network.Intersection;

import team.traffic.domain.network.Road;
import team.traffic.domain.network.RoadCondition;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.network.RoadStatus;

class PlaceResolverTest {
    @Test
    void resolvesEverySelectorToConcreteRoutablePlaces() {
        RoadNetwork network = squareNetwork();
        network.addBuilding(new Building(
                "home-1",
                "Home",
                BuildingType.RESIDENTIAL,
                2,
                2,
                null,
                null,
                List.of(
                        new BuildingAccessPoint("far", "NW", 20),
                        new BuildingAccessPoint("near", "SW", 5))));
        ZoneDefinition zone = new ZoneDefinition(
                "west",
                Set.of("home-1"),
                Set.of("NW"));
        PlaceResolver resolver = new PlaceResolver(network, Map.of(zone.id(), zone));

        assertThat(resolver.resolve(
                        PlaceSpec.buildings(Set.of(BuildingType.RESIDENTIAL)), null))
                .containsExactly(ResolvedPlace.building("home-1", "SW"));
        assertThat(resolver.resolve(PlaceSpec.boundary(BoundarySide.NORTH), null))
                .extracting(ResolvedPlace::id)
                .containsExactlyInAnyOrder("NW", "NE");
        assertThat(resolver.resolve(PlaceSpec.zones(Set.of("west")), null))
                .containsExactlyInAnyOrder(
                        ResolvedPlace.building("home-1", "SW"),
                        ResolvedPlace.intersection("NW"));
        assertThat(resolver.resolve(
                        PlaceSpec.origin(), ResolvedPlace.boundary("SE")))
                .containsExactly(ResolvedPlace.boundary("SE"));
        assertThat(resolver.resolve(
                        PlaceSpec.explicitIntersection("NE"), null))
                .containsExactly(ResolvedPlace.intersection("NE"));
    }

    @Test
    void rejectsSelectorsThatResolveNothing() {
        PlaceResolver resolver = new PlaceResolver(squareNetwork(), Map.of());

        assertThatThrownBy(() -> resolver.resolve(
                PlaceSpec.buildings(Set.of(BuildingType.HOSPITAL)), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("resolves no candidate");
        assertThatThrownBy(() -> resolver.resolve(
                PlaceSpec.zones(Set.of("missing")), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Zone not found");
    }

    @Test
    void boundaryOriginsRequireOutgoingRoadsAndDestinationsRequireIncomingRoads() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(intersection("SOUTH", 5, 0));
        network.addIntersection(intersection("CENTRE", 5, 5));
        network.addIntersection(intersection("NORTH-ENTRY", 0, 10));
        network.addIntersection(intersection("NORTH-EXIT", 10, 10));
        addRoad(network, "ENTRY-CENTRE", "NORTH-ENTRY", "CENTRE");
        addRoad(network, "CENTRE-EXIT", "CENTRE", "NORTH-EXIT");
        PlaceResolver resolver = new PlaceResolver(network, Map.of());

        assertThat(resolver.resolve(
                        PlaceSpec.boundary(BoundarySide.NORTH),
                        null))
                .containsExactly(ResolvedPlace.boundary("NORTH-ENTRY"));
        assertThat(resolver.resolve(
                        PlaceSpec.boundary(BoundarySide.NORTH),
                        ResolvedPlace.intersection("CENTRE")))
                .containsExactly(ResolvedPlace.boundary("NORTH-EXIT"));
    }

    private static RoadNetwork squareNetwork() {
        RoadNetwork network = new RoadNetwork();
        network.addIntersection(intersection("SW", 0, 0));
        network.addIntersection(intersection("SE", 10, 0));
        network.addIntersection(intersection("NW", 0, 10));
        network.addIntersection(intersection("NE", 10, 10));
        addRoad(network, "SW-SE", "SW", "SE");
        addRoad(network, "SE-NE", "SE", "NE");
        addRoad(network, "NE-NW", "NE", "NW");
        addRoad(network, "NW-SW", "NW", "SW");
        return network;
    }

    private static Intersection intersection(String id, double x, double y) {
        return new Intersection(id, id, x, y);
    }

    private static void addRoad(RoadNetwork network, String id, String from, String to) {
        network.addRoad(new Road(
                id,
                from,
                to,
                0.1,
                40,
                20,
                0,
                new RoadCondition(0, 0, RoadStatus.OPEN)));
    }
}
