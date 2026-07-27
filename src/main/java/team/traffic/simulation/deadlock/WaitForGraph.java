package team.traffic.simulation.deadlock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import team.traffic.domain.vehicle.Vehicle;
import team.traffic.domain.vehicle.dynamics.Lane;
import team.traffic.domain.vehicle.dynamics.RoadOccupancy;
import team.traffic.domain.vehicle.dynamics.TrafficParticipant;
import team.traffic.domain.vehicle.dynamics.TrafficState;
import team.traffic.domain.vehicle.dynamics.WaitCause;
import team.traffic.domain.vehicle.dynamics.WaitResource;
import team.traffic.domain.vehicle.dynamics.WaitState;

/**
 * Deterministic bipartite graph:
 * vehicle -> required resource -> vehicles currently owning that resource.
 */
public final class WaitForGraph {
    private static final String VEHICLE_PREFIX = "V:";
    private static final String RESOURCE_PREFIX = "R:";

    private final Map<String, Set<String>> edges;

    public WaitForGraph() {
        this.edges = new TreeMap<>();
    }

    public void addWait(String vehicleId, WaitResource resource) {
        requireId(vehicleId, "Vehicle id");
        if (resource == null) throw new IllegalArgumentException("Wait resource is required.");
        addEdge(vehicleNode(vehicleId), resourceNode(resource.stableKey()));
    }

    public void addOwner(WaitResource resource, String vehicleId) {
        if (resource == null) throw new IllegalArgumentException("Wait resource is required.");
        requireId(vehicleId, "Vehicle id");
        addEdge(resourceNode(resource.stableKey()), vehicleNode(vehicleId));
    }

    public Map<String, Set<String>> edges() {
        Map<String, Set<String>> copy = new LinkedHashMap<>();
        edges.forEach((node, targets) -> copy.put(node, Set.copyOf(targets)));
        return Collections.unmodifiableMap(copy);
    }

    public List<Component> cyclicComponents() {
        Tarjan tarjan = new Tarjan(edges);
        List<Component> result = new ArrayList<>();
        for (Set<String> component : tarjan.components()) {
            boolean selfCycle = component.size() == 1
                    && edges.getOrDefault(component.iterator().next(), Set.of())
                            .contains(component.iterator().next());
            if (component.size() < 2 && !selfCycle) continue;
            Set<String> vehicles = new TreeSet<>();
            Set<String> resources = new TreeSet<>();
            for (String node : component) {
                if (node.startsWith(VEHICLE_PREFIX)) {
                    vehicles.add(node.substring(VEHICLE_PREFIX.length()));
                } else if (node.startsWith(RESOURCE_PREFIX)) {
                    resources.add(node.substring(RESOURCE_PREFIX.length()));
                }
            }
            if (!vehicles.isEmpty() && !resources.isEmpty()) {
                result.add(new Component(
                        fingerprint(vehicles, resources),
                        vehicles,
                        resources));
            }
        }
        result.sort(java.util.Comparator.comparing(Component::fingerprint));
        return List.copyOf(result);
    }

    public static WaitForGraph capture(
            List<Vehicle> vehicles,
            TrafficState state) {
        if (state == null) throw new IllegalArgumentException("Traffic state is required.");
        WaitForGraph graph = new WaitForGraph();
        List<Vehicle> ordered = vehicles == null
                ? List.of()
                : vehicles.stream().sorted(
                        java.util.Comparator.comparing(Vehicle::getId)).toList();
        double now = state.getElapsedSeconds();
        for (Vehicle vehicle : ordered) {
            WaitState wait = vehicle.getWaitState();
            if (!eligible(wait, now) || wait.resource().isEmpty()) continue;
            WaitResource resource = wait.resource().get();
            graph.addWait(vehicle.getId(), resource);
            for (String ownerId : owners(resource, state)) {
                if (!ownerId.equals(vehicle.getId())) {
                    graph.addOwner(resource, ownerId);
                }
            }
        }
        return graph;
    }

    private static boolean eligible(WaitState wait, double now) {
        if (wait == null || !wait.isWaiting()) return false;
        if (wait.expectedReleaseAtSeconds().isPresent()
                && wait.expectedReleaseAtSeconds().getAsDouble() > now) {
            return false;
        }
        return wait.cause() != WaitCause.RED_SIGNAL
                && wait.cause() != WaitCause.DWELL
                && wait.cause() != WaitCause.EVENT_STOPPED;
    }

    private static Set<String> owners(WaitResource resource, TrafficState state) {
        Set<String> owners = new TreeSet<>();
        if (resource instanceof WaitResource.Leader leader) {
            owners.add(leader.vehicleId());
        } else if (resource instanceof WaitResource.LaneEntry entry) {
            RoadOccupancy occupancy = state.occupancyOf(entry.roadId());
            Lane lane = occupancy == null ? null : occupancy.lane(entry.laneIndex());
            if (lane != null) {
                lane.getVehicles().stream()
                        .map(TrafficParticipant::getId)
                        .forEach(owners::add);
            }
        } else if (resource instanceof WaitResource.JunctionMovement movement) {
            addOccupants(owners, state.occupancyOf(movement.toRoadId()));
        } else if (resource instanceof WaitResource.RoadExit exit) {
            addOccupants(owners, state.occupancyOf(exit.roadId()));
        }
        return owners;
    }

    private static void addOccupants(
            Set<String> owners,
            RoadOccupancy occupancy) {
        if (occupancy != null) {
            occupancy.getVehicles().stream()
                    .map(TrafficParticipant::getId)
                    .forEach(owners::add);
        }
    }

    private void addEdge(String from, String to) {
        edges.computeIfAbsent(from, ignored -> new TreeSet<>()).add(to);
        edges.computeIfAbsent(to, ignored -> new TreeSet<>());
    }

    private static String vehicleNode(String id) {
        return VEHICLE_PREFIX + id;
    }

    private static String resourceNode(String key) {
        return RESOURCE_PREFIX + key;
    }

    private static String fingerprint(
            Set<String> vehicles,
            Set<String> resources) {
        return String.join(",", vehicles) + "|" + String.join(",", resources);
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required.");
        }
    }

    public record Component(
            String fingerprint,
            Set<String> vehicleIds,
            Set<String> resourceKeys) {
        public Component {
            vehicleIds = Collections.unmodifiableSet(
                    new LinkedHashSet<>(new TreeSet<>(vehicleIds)));
            resourceKeys = Collections.unmodifiableSet(
                    new LinkedHashSet<>(new TreeSet<>(resourceKeys)));
        }
    }

    private static final class Tarjan {
        private final Map<String, Set<String>> graph;
        private final Map<String, Integer> indexes = new HashMap<>();
        private final Map<String, Integer> lowLinks = new HashMap<>();
        private final Deque<String> stack = new ArrayDeque<>();
        private final Set<String> onStack = new HashSet<>();
        private final List<Set<String>> components = new ArrayList<>();
        private int nextIndex;

        private Tarjan(Map<String, Set<String>> graph) {
            this.graph = graph;
        }

        private List<Set<String>> components() {
            for (String node : new TreeSet<>(graph.keySet())) {
                if (!indexes.containsKey(node)) visit(node);
            }
            return components;
        }

        private void visit(String node) {
            indexes.put(node, nextIndex);
            lowLinks.put(node, nextIndex);
            nextIndex++;
            stack.push(node);
            onStack.add(node);
            for (String target : graph.getOrDefault(node, Set.of())) {
                if (!indexes.containsKey(target)) {
                    visit(target);
                    lowLinks.put(node, Math.min(lowLinks.get(node), lowLinks.get(target)));
                } else if (onStack.contains(target)) {
                    lowLinks.put(node, Math.min(lowLinks.get(node), indexes.get(target)));
                }
            }
            if (!lowLinks.get(node).equals(indexes.get(node))) return;
            Set<String> component = new LinkedHashSet<>();
            String member;
            do {
                member = stack.pop();
                onStack.remove(member);
                component.add(member);
            } while (!member.equals(node));
            components.add(component);
        }
    }
}
