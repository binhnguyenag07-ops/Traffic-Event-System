import { memo, useEffect, useMemo, useState } from 'react'
import L from 'leaflet'
import {
  CircleMarker,
  MapContainer,
  Marker,
  Pane,
  Polyline,
  Popup,
  TileLayer,
  Tooltip,
  useMap,
  useMapEvents,
} from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import {
  HCMC_CENTER,
  HCMC_DISTRICT_BOUNDS,
  positionAlongPath,
  positionFor,
  positionsForRoad,
  roadColor,
} from './mapUtils'

const TILE_URL = import.meta.env.VITE_TILE_URL || 'https://tile.openstreetmap.org/{z}/{x}/{y}.png'
const TILE_ATTRIBUTION = import.meta.env.VITE_TILE_ATTRIBUTION
  || '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'

function FitNetwork({ positions }) {
  const map = useMap()
  useEffect(() => {
    if (positions.length > 1) map.fitBounds(positions, { padding: [44, 44], maxZoom: 16 })
  }, [map, positions])
  return null
}

function MapCoordinatePicker({ active, onPick }) {
  useMapEvents({
    click(event) {
      if (active) onPick({ latitude: event.latlng.lat, longitude: event.latlng.lng })
    },
  })
  return null
}

function divIcon(className, label, size = 32) {
  return L.divIcon({
    className: '',
    html: `<span class="${className}" aria-hidden="true">${label}</span>`,
    iconSize: [size, size],
    iconAnchor: [size / 2, size / 2],
  })
}

const vehicleGlyph = { CAR: 'C', BUS: 'B', EMERGENCY: '+' }
const eventGlyph = { CONGESTION: '≋', ACCIDENT: '!', ROAD_CLOSURE: '×', TRAFFIC_LIGHT_DELAY: '◉' }

function MapLegend() {
  return (
    <div className="map-legend" aria-label="Traffic legend">
      <span><i className="legend-line free" />Free</span>
      <span><i className="legend-line busy" />Busy</span>
      <span><i className="legend-line severe" />Severe</span>
      <span><i className="legend-line closed" />Closed</span>
    </div>
  )
}

function TrafficMap({ intersections, roads, vehicles, events, route, selected, onSelect, onPickIntersection,
  captureIntersection = false, draftIntersection = null, highlightedRoadIds = [], onMapCoordinate = () => {} }) {
  const [layers, setLayers] = useState({ traffic: true, vehicles: true, events: true, labels: true })
  const intersectionById = useMemo(
    () => new Map(intersections.map((intersection) => [intersection.id, intersection])),
    [intersections],
  )
  const positions = useMemo(() => intersections.map(positionFor), [intersections])
  const roadById = useMemo(() => new Map(roads.map((road) => [road.id, road])), [roads])
  const routeRoadIds = useMemo(() => new Set((route?.steps || []).map((step) => step.roadId)), [route])
  const highlightedRoadSet = useMemo(() => new Set(highlightedRoadIds), [highlightedRoadIds])

  const toggleLayer = (name) => setLayers((current) => ({ ...current, [name]: !current[name] }))

  return (
    <section className={`map-shell ${captureIntersection ? 'coordinate-pick-mode' : ''}`} aria-label="Interactive traffic map">
      <div className="map-toolbar">
        <div>
          <span className="eyebrow">Live network</span>
          <strong>{intersections.length} junctions · {roads.length} directed roads</strong>
        </div>
        <div className="layer-toggles" aria-label="Map layers">
          {Object.entries(layers).map(([name, visible]) => (
            <button key={name} className={visible ? 'active' : ''} aria-pressed={visible}
              onClick={() => toggleLayer(name)}>
              {name}
            </button>
          ))}
        </div>
      </div>

      <MapContainer
        center={HCMC_CENTER}
        zoom={16}
        minZoom={16}
        maxBounds={HCMC_DISTRICT_BOUNDS}
        maxBoundsViscosity={1}
        preferCanvas
        className="traffic-map"
      >
        <TileLayer attribution={TILE_ATTRIBUTION} url={TILE_URL} />
        <FitNetwork positions={positions} />
        <MapCoordinatePicker active={captureIntersection} onPick={onMapCoordinate} />

        <Pane name="roads" style={{ zIndex: 410 }}>
          {roads.map((road) => {
            const start = intersectionById.get(road.startIntersectionId)
            const end = intersectionById.get(road.endIntersectionId)
            if (!start || !end) return null
            const isRoute = routeRoadIds.has(road.id)
            const isHighlighted = highlightedRoadSet.has(road.id)
            return (
              <Polyline
                key={road.id}
                positions={positionsForRoad(road, start, end)}
                pathOptions={{
                  color: isHighlighted ? '#00a9c7' : layers.traffic ? roadColor(road) : '#77838f',
                  weight: isHighlighted ? 9 : isRoute ? 10 : 4 + Math.max(0, Number(road.vehicleDensity || 0)) * 3,
                  opacity: isHighlighted ? 1 : isRoute ? 0.35 : 0.86,
                  dashArray: road.status === 'BLOCKED' || road.status === 'CLOSED' ? '8 7' : undefined,
                  lineCap: 'round',
                }}
                eventHandlers={{ click: () => onSelect({ kind: 'road', data: road }) }}
              >
                <Tooltip sticky>{road.id} · {road.status} · {(Number(road.congestionLevel || 0) * 100).toFixed(0)}%</Tooltip>
              </Polyline>
            )
          })}
        </Pane>

        {route?.steps?.length ? (
          <Pane name="route" style={{ zIndex: 430 }}>
            {route.steps.map((step, index) => {
              const start = intersectionById.get(step.from)
              const end = intersectionById.get(step.to)
              if (!start || !end) return null
              const road = roadById.get(step.roadId)
              const path = positionsForRoad(road, start, end)
              const midpoint = positionAlongPath(path, 0.5)
              return [
                <Polyline key={`${step.roadId}-line`} positions={path}
                  pathOptions={{ color: '#1677ff', weight: 6, opacity: 0.95, lineCap: 'round' }} />,
                <Marker key={`${step.roadId}-arrow`} position={midpoint}
                  icon={divIcon('route-arrow', '›', 22)} interactive={false} zIndexOffset={300 + index} />,
              ]
            })}
          </Pane>
        ) : null}

        <Pane name="intersections" style={{ zIndex: 440 }}>
          {intersections.map((intersection) => (
            <CircleMarker
              key={intersection.id}
              center={positionFor(intersection)}
              radius={selected?.kind === 'intersection' && selected.data.id === intersection.id ? 10 : 7}
              pathOptions={{ color: '#ffffff', weight: 3, fillColor: '#172631', fillOpacity: 1 }}
              eventHandlers={{
                click: () => {
                  onSelect({ kind: 'intersection', data: intersection })
                  if (!captureIntersection) onPickIntersection(intersection.id)
                },
              }}
            >
              {layers.labels ? <Tooltip permanent direction="top" offset={[0, -8]}>{intersection.id}</Tooltip> : null}
              <Popup><strong>{intersection.name}</strong><br />{intersection.locationType}</Popup>
            </CircleMarker>
          ))}
        </Pane>

        {draftIntersection?.latitude != null ? (
          <CircleMarker center={[draftIntersection.latitude, draftIntersection.longitude]} radius={11}
            pathOptions={{ color: '#ffffff', weight: 3, fillColor: '#00a9c7', fillOpacity: 0.9 }}>
            <Tooltip permanent direction="top">New junction</Tooltip>
          </CircleMarker>
        ) : null}

        {layers.events ? events.map((event) => {
          const road = roadById.get(event.affectedRoadId)
          const start = road && intersectionById.get(road.startIntersectionId)
          const end = road && intersectionById.get(road.endIntersectionId)
          if (!start || !end) return null
          const path = positionsForRoad(road, start, end)
          return (
            <Marker key={event.id} position={positionAlongPath(path, 0.5)}
              icon={divIcon(`event-pin ${event.status.toLowerCase()}`, eventGlyph[event.type] || '!', 30)}
              eventHandlers={{ click: () => onSelect({ kind: 'event', data: event }) }}>
              <Tooltip>{event.type.replaceAll('_', ' ')} · {event.status}</Tooltip>
            </Marker>
          )
        }) : null}

        {layers.vehicles ? vehicles.map((vehicle) => {
          const start = intersectionById.get(vehicle.roadStartIntersectionId || vehicle.currentIntersectionId)
          const end = intersectionById.get(vehicle.roadEndIntersectionId)
          if (!start) return null
          const road = roadById.get(vehicle.occupiedRoadId)
          const path = end ? positionsForRoad(road, start, end) : [positionFor(start)]
          const progress = Math.max(0, Math.min(1, Number(vehicle.roadProgress || 0)))
          const position = positionAlongPath(path, progress)
          return (
            <Marker key={vehicle.id} position={position}
              icon={divIcon(`vehicle-pin ${vehicle.type.toLowerCase()} ${vehicle.status.toLowerCase()}`,
                vehicleGlyph[vehicle.type] || 'V', 34)}
              eventHandlers={{ click: () => onSelect({ kind: 'vehicle', data: vehicle }) }}>
              <Tooltip>{vehicle.id} · {vehicle.status}</Tooltip>
            </Marker>
          )
        }) : null}
      </MapContainer>
      <MapLegend />
    </section>
  )
}

export default memo(TrafficMap)
