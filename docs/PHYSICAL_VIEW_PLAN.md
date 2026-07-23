# Macro and Physical Map Plan

## Status

The Leaflet screen is the implemented **macro view**. It renders the road
network, pressure, incidents, selected routes, editing controls, and small
glowing type-coloured vehicle dots. The dots come directly from the selected
authoritative frame and use CSS/Leaflet markers rather than sprite assets. Its
footer scrubs retained frames and can return explicitly to LIVE mode.

The **physical view** described below is a target design, not an implemented
feature. It should be built as a separate renderer instead of adding thousands
of animated markers back to Leaflet.

## Two-view boundary

| View | Renderer | Responsibility |
|---|---|---|
| Macro | React Leaflet | Browse the full network, inspect pressure/incidents/routes, view lightweight vehicle dots, and select an area |
| Physical | PixiJS/WebGL canvas | Animate lane-level vehicles, stop lines, traffic lights, and junction movements |

At a high Leaflet zoom (initially zoom 18 or 19), show an **Open physical
view** action for the selected junction or road. Do not replace Leaflet
automatically on the first zoom event: an explicit transition preserves the
user's selection and avoids a disorienting renderer switch.

Both views must consume the same `{timelineId, frameId}` rather than maintaining
independent clocks. The macro view uses aggregate road state; the physical view
requests only the geometry and moving objects within its selected region.

Continuous lateral pose is now part of the domain contract: a vehicle carries a
signed offset in metres from the road centreline and crosses to a new lane at a
bounded lateral speed, so a vehicle part-way through a lane change reports a
position it genuinely occupies. The renderer may interpolate between those
offsets and should not derive lateral position from the lane index.

Sub-lane occupancy has since landed too. A motorcycle can now ride the gap
between two lanes, and the offset it reports is a position it genuinely
occupies, so the physical renderer may draw filtering as it happens. See
`MOTORCYCLE_FILTERING_PLAN.md` for the model and its limits.

## Implemented replay contract

The backend already records one immutable `SimulationFrame` after every
microscopic sub-step (at most one simulated second) and exact event boundary.
It contains `tick`, `elapsedSeconds`, vehicle poses, event states, road states,
and traffic-signal phase state. The current frame is sent through SSE; retained
frames are listed and queried through:

```text
GET /api/simulation/trace/frames
GET /api/simulation/trace/frames/at?elapsedSeconds=...
```

The physical renderer must receive the exact frame object selected by the
shared cursor. It may interpolate pixels between adjacent LIVE frames for
smoothness, but interpolation must never create authoritative event, signal,
road, or vehicle state. On pause or history scrub it must draw the selected
frame exactly. This makes logical synchronization enforceable even though two
renderers cannot paint pixels in the same nanosecond.

The history retains 900 timestamps in memory. Reset/initialize and every
network-geometry edit start a new timeline; the client discards a selected
history frame when its timeline changes or the frame leaves the retained
window.

## OSM ingestion decision

`tools/generate_osm_map.py` currently sends raw Overpass requests with
`urllib`. That is suitable for rebuilding the present centreline network, but
it is not a reliable physical-view pipeline: it depends on public mirrors and
does not preserve enough lane, signal, turn, building, or land-use data.

Use this reproducible pipeline instead:

```text
pinned city .osm.pbf
  -> osmium extract (project bounding box/polygon)
  -> Pyrosm feature extraction and projection
  -> project normalizer/validator
  -> simulation JSON + physical-scene JSON
```

- **Osmium** makes a small geographic extract and can pre-filter tags while
  retaining referenced nodes and relation members.
- **Pyrosm** reads the local PBF, returns driving roads and other layers as
  GeoDataFrames, and avoids a live Overpass dependency during generation.
- The project normalizer remains repository-owned. It converts OSM's partial
  tagging into stable domain fields and records whether every value came from
  OSM or from an explicit fallback rule.

OSMnx is useful for graph analysis, but its normal `graph_from_*` download
functions still query Overpass. It is therefore not the preferred replacement
when reproducible offline extraction is the primary requirement.

### Required source tags

| Feature | Preserve when present |
|---|---|
| Road way | `highway`, `name`, `oneway`, `junction`, `maxspeed`, `width`, `surface` |
| Lane model | `lanes`, `lanes:forward`, `lanes:backward`, `turn:lanes`, `change:lanes` |
| Signal node | `highway=traffic_signals`, `traffic_signals:direction` |
| Junction detail | `type=restriction`, crossings, stop/give-way nodes |
| Scene context | `building`, `landuse`, `natural`, and relevant amenity polygons |

OSM coverage is incomplete, especially for `lanes`, `width`, and signal
direction. Missing values must use deterministic road-class defaults, with
metadata such as `laneCountSource: "OSM"` or `"INFERRED"`; absence must not be
silently presented as measured data.

## Physical scene contract

The physical renderer should use local metre coordinates around the selected
area, not latitude/longitude for every frame. A first API boundary can be:

```text
GET /api/simulation/physical-view?lat=...&lon=...&radiusMetres=...
```

Its response should contain:

- lane centre lines or polygons, lane direction, and permitted turns;
- junction pavement, stop-line positions, and lane-to-lane connector curves;
- signal head position, controlled approaches, phase, and remaining time;
- vehicles in the region with `id`, `type`, `laneId`, metre position, heading,
  speed, and status;
- optional building, green-space, and water polygons for the stylised scene.

The authoritative frame stream carries the time sequence. Initially the client
may filter its vehicle list; once the network grows, publish a
region-scoped physical stream so the browser does not receive every vehicle.

## Renderer layers

Build the PixiJS scene in a stable draw order:

1. ground, buildings, water, and vegetation;
2. road pavement, medians, sidewalks, and lane markings;
3. stop lines and traffic-signal heads;
4. vehicle sprites, positioned from simulation state and interpolated between
   authoritative ticks;
5. selection and optional debug overlays.

The reference artwork is a useful visual direction, not a ready map source.
OSM supplies geometry and tags; the application still needs a style system,
texture atlas, lane/junction geometry builder, and sprite interpolation.

## Delivery sequence

1. Replace the legacy generator with a pinned-PBF Pyrosm normalizer and add
   fixture tests for lane/signal tag parsing.
2. Add lane, stop-line, signal-association, and local-coordinate DTOs without
   changing routing behaviour.
3. Implement one junction in a standalone PixiJS physical-view component.
4. Connect the component to the existing shared frame cursor and interpolate
   only visual poses between adjacent LIVE frames.
5. Add the high-zoom Leaflet action while preserving the selected
   `{timelineId, frameId}` across the renderer transition.
6. Add buildings/land use, culling, texture atlases, and region-scoped streams
   only after the lane/signal geometry is correct.
