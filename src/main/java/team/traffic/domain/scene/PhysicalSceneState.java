package team.traffic.domain.scene;

import java.util.List;
import team.traffic.domain.network.GeoPoint;

/**
 * The fast-changing half of a physical scene.
 *
 * <p>Geometry is intentionally absent. The browser keeps the full static map
 * and polls only signals and vehicles, so a simulation frame does not resend
 * megabytes of lanes, land cover and building footprints.</p>
 */
public record PhysicalSceneState(
        GeoPoint origin,
        double radiusMetres,
        List<SceneSignal> signals,
        List<SceneVehicle> vehicles) {

    public PhysicalSceneState {
        if (origin == null) {
            throw new IllegalArgumentException("Scene origin is required.");
        }
        if (!Double.isFinite(radiusMetres) || radiusMetres <= 0) {
            throw new IllegalArgumentException("Scene radius must be positive.");
        }
        signals = signals == null ? List.of() : List.copyOf(signals);
        vehicles = vehicles == null ? List.of() : List.copyOf(vehicles);
    }
}
