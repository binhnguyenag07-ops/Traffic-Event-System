import { LocateFixed, Maximize2, Upload } from 'lucide-react';

export function MapViewport({ intersections, roads, vehicles, selectedRoute }) {
  return (
    <section className="map-panel">
      <div className="map-toolbar">
        <div>
          <h2>Network Map</h2>
          <span>{intersections.length} nodes · {roads.length} roads · {vehicles.length} vehicles</span>
        </div>
        <div className="icon-row">
          <button type="button" title="Load map" aria-label="Load map">
            <Upload size={18} aria-hidden="true" />
          </button>
          <button type="button" title="Center map" aria-label="Center map">
            <LocateFixed size={18} aria-hidden="true" />
          </button>
          <button type="button" title="Fullscreen" aria-label="Fullscreen">
            <Maximize2 size={18} aria-hidden="true" />
          </button>
        </div>
      </div>
      <div className="map-surface" data-route-selected={Boolean(selectedRoute)}>
        <div className="map-node node-a" />
        <div className="map-node node-b" />
        <div className="map-node node-c" />
        <div className="map-road road-a" />
        <div className="map-road road-b" />
      </div>
    </section>
  );
}
