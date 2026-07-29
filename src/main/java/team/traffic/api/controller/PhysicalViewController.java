package team.traffic.api.controller;

import io.javalin.Javalin;
import io.javalin.http.Context;
import java.util.List;
import java.util.Optional;
import team.traffic.api.dto.PhysicalViewDto;
import team.traffic.api.dto.PhysicalViewStateDto;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.domain.scene.PhysicalScene;
import team.traffic.domain.scene.PhysicalSceneAssembler;
import team.traffic.domain.scene.PhysicalSceneState;
import team.traffic.domain.scene.SceneVehicle;
import team.traffic.domain.scene.SignalSnapshot;
import team.traffic.service.RoadNetworkService;
import team.traffic.service.SimulationService;
import team.traffic.simulation.history.SimulationFrame;
import team.traffic.domain.vehicle.VehicleStatus;

/**
 * Serves the lane-level scene around a point for the physical renderer.
 *
 * <p>It joins the two halves the renderer needs but the model keeps apart: the
 * static lane geometry, derived from the network, and the moving vehicles, taken
 * from the current authoritative frame. Both are returned tied to that frame's
 * {@code timelineId} and {@code frameId} so the physical view stays on the same
 * clock as the macro map.</p>
 */
public class PhysicalViewController {

    /** Largest ad-hoc region; explicit full-map requests are bounded by the loaded network. */
    private static final double MAX_RADIUS_METRES = 1_000.0;
    /** Metres per second in one kilometre per hour, for the speed the frame reports in kph. */
    private static final double KPH_TO_MPS = 3.6;

    private final RoadNetworkService networkService;
    private final SimulationService simulationService;

    public PhysicalViewController(
            RoadNetworkService networkService, SimulationService simulationService) {
        this.networkService = networkService;
        this.simulationService = simulationService;
    }

    public void registerRoutes(Javalin app) {
        app.get("/api/simulation/physical-view", this::getPhysicalView);
        app.get("/api/simulation/physical-view/state", this::getPhysicalViewState);
    }

    private void getPhysicalView(Context ctx) {
        Double latitude = parse(ctx, "lat");
        Double longitude = parse(ctx, "lon");
        Double radius = parse(ctx, "radiusMetres");
        boolean fullMap = Boolean.parseBoolean(ctx.queryParam("fullMap"));
        if (latitude == null || longitude == null || (!fullMap && radius == null)) {
            ctx.status(400).json(error(
                    fullMap
                            ? "lat and lon are required and numeric."
                            : "lat, lon and radiusMetres are all required and numeric."));
            return;
        }
        if (!fullMap && radius <= 0) {
            ctx.status(400).json(error("radiusMetres must be positive."));
            return;
        }

        RoadNetwork network = networkService.getNetwork();
        Optional<SimulationFrame> frame = simulationService.getCurrentFrame();
        List<SceneVehicle> vehicles = frame
                .map(currentFrame -> sceneVehicles(currentFrame))
                .orElse(List.of());

        List<SignalSnapshot> signals = frame
                .map(PhysicalViewController::signalSnapshots)
                .orElse(List.of());

        PhysicalSceneAssembler assembler = new PhysicalSceneAssembler(network);
        PhysicalScene scene = fullMap
                ? assembler.sceneAll(latitude, longitude, vehicles, signals)
                : assembler.sceneAround(
                        latitude,
                        longitude,
                        Math.min(radius, MAX_RADIUS_METRES),
                        vehicles,
                        signals);

        ctx.json(PhysicalViewDto.of(
                scene,
                frame.map(SimulationFrame::timelineId).orElse("none"),
                frame.map(SimulationFrame::frameId).orElse(-1L),
                frame.map(SimulationFrame::elapsedSeconds).orElse(0.0)));
    }

    private void getPhysicalViewState(Context ctx) {
        Double latitude = parse(ctx, "lat");
        Double longitude = parse(ctx, "lon");
        Double radius = parse(ctx, "radiusMetres");
        if (latitude == null || longitude == null || radius == null) {
            ctx.status(400).json(error("lat, lon and radiusMetres are all required and numeric."));
            return;
        }
        if (radius <= 0) {
            ctx.status(400).json(error("radiusMetres must be positive."));
            return;
        }

        Optional<SimulationFrame> frame = simulationService.getCurrentFrame();
        List<SceneVehicle> vehicles = frame
                .map(PhysicalViewController::sceneVehicles)
                .orElse(List.of());
        List<SignalSnapshot> signals = frame
                .map(PhysicalViewController::signalSnapshots)
                .orElse(List.of());
        PhysicalSceneState state = new PhysicalSceneAssembler(networkService.getNetwork())
                .stateAround(latitude, longitude, radius, vehicles, signals);

        ctx.json(PhysicalViewStateDto.of(
                state,
                frame.map(SimulationFrame::timelineId).orElse("none"),
                frame.map(SimulationFrame::frameId).orElse(-1L),
                frame.map(SimulationFrame::elapsedSeconds).orElse(0.0),
                simulationService.getPlaybackIntervalMillis()));
    }

    /**
     * The frame already resolved which approach is green and for how long; this
     * only carries it across, so the lights drawn cannot disagree with the
     * lights the vehicles are obeying.
     */
    private static List<SignalSnapshot> signalSnapshots(SimulationFrame frame) {
        return frame.trafficSignals().stream()
                .map(signal -> new SignalSnapshot(
                        signal.junctionId(),
                        signal.greenIncomingRoadIds(),
                        signal.phaseRemainingSeconds()))
                .toList();
    }

    private static List<SceneVehicle> sceneVehicles(SimulationFrame frame) {
        return frame.vehicles().stream()
                .filter(vehicle -> vehicle.status() != VehicleStatus.DWELLING
                        && vehicle.status() != VehicleStatus.ARRIVED)
                .filter(vehicle -> vehicle.latitude() != null && vehicle.longitude() != null)
                .map(PhysicalViewController::toSceneVehicle)
                .toList();
    }

    private static SceneVehicle toSceneVehicle(SimulationFrame.VehicleState vehicle) {
        return new SceneVehicle(
                vehicle.id(),
                vehicle.type().name(),
                vehicle.latitude(),
                vehicle.longitude(),
                vehicle.headingDegrees() == null ? 0.0 : vehicle.headingDegrees(),
                vehicle.instantaneousSpeed() / KPH_TO_MPS,
                vehicle.length(),
                vehicle.width(),
                vehicle.laneIndex(),
                vehicle.lateralOffsetMetres(),
                vehicle.occupiedRoadId(),
                vehicle.roadProgress(),
                vehicle.inJunction(),
                vehicle.junctionFromRoadId(),
                vehicle.junctionToRoadId(),
                vehicle.journeyPhase(),
                vehicle.originPlaceKind(),
                vehicle.originPlaceId(),
                vehicle.originAccessIntersectionId(),
                vehicle.destinationPlaceKind(),
                vehicle.destinationPlaceId(),
                vehicle.destinationAccessIntersectionId());
    }

    private static Double parse(Context ctx, String name) {
        String raw = ctx.queryParam(name);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static java.util.Map<String, String> error(String message) {
        return java.util.Map.of("error", message);
    }
}
