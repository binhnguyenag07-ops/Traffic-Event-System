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
  roadClassStyle,
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

function MapZoomTracker({ onZoom, onCenter }) {
  const map = useMapEvents({
    zoomend: () => { onZoom(map.getZoom()); onCenter?.(map.getCenter()) },
    moveend: () => onCenter?.(map.getCenter()),
  })
  useEffect(() => { onZoom(map.getZoom()); onCenter?.(map.getCenter()) }, [map, onZoom, onCenter])
  return null
}

// Where the physical view should open: the selected junction if it carries
// coordinates, otherwise the middle of what the user is looking at.
function physicalTargetFrom(selected, mapCenter, radiusMetres = 220) {
  const data = selected?.data
  if (data && data.latitude != null && data.longitude != null) {
    return { latitude: Number(data.latitude), longitude: Number(data.longitude), radiusMetres }
  }
  if (mapCenter) return { latitude: mapCenter.lat, longitude: mapCenter.lng, radiusMetres }
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

const eventGlyph = { CONGESTION: '≋', ACCIDENT: '!', BREAKDOWN: '⚒', ROAD_CLOSURE: '×' }
const vehicleDotSizes = { CAR: 8, MOTORCYCLE: 6, BUS: 10, EMERGENCY: 9 }

function positionForVehicle(vehicle, roadById, intersectionById) {
  if (vehicle.latitude != null && vehicle.longitude != null) {
    const latitude = Number(vehicle.latitude)
    const longitude = Number(vehicle.longitude)
    if (Number.isFinite(latitude) && Number.isFinite(longitude)) return [latitude, longitude]
  }
  const road = roadById.get(vehicle.occupiedRoadId)
  const start = road && intersectionById.get(road.startIntersectionId)
  const end = road && intersectionById.get(road.endIntersectionId)
  if (!road || !start || !end) return null
  const progress = Math.max(0, Math.min(1, Number(vehicle.roadProgress || 0)))
  return positionAlongPath(positionsForRoad(road, start, end), progress)
}

function vehicleDotIcon(vehicle, zoom) {
  const type = String(vehicle.type || 'CAR').toUpperCase()
  const status = String(vehicle.status || 'WAITING').toLowerCase()
  const size = (vehicleDotSizes[type] || vehicleDotSizes.CAR) + Math.max(0, zoom - 16) * 0.7
  return divIcon(`macro-vehicle-dot ${type.toLowerCase()} ${status}`, '', size)
}

function MapLegend() {
  return (
    <div className="map-legend" aria-label="Traffic legend">
      <span><i className="legend-line free" />Free</span>
      <span><i className="legend-line busy" />Busy</span>
      <span><i className="legend-line severe" />Severe</span>
      <span><i className="legend-line closed" />Closed</span>
      <span><i className="vehicle-legend-dot car" />Car</span>
      <span><i className="vehicle-legend-dot motorcycle" />Motorcycle</span>
      <span><i className="vehicle-legend-dot bus" />Bus</span>
      <span><i className="vehicle-legend-dot emergency" />Emergency</span>
    </div>
  )
}

function TrafficMap({ intersections, roads, vehicles = [], events, route, selected, onSelect, onPickIntersection,
  captureIntersection = false, draftIntersection = null, highlightedRoadIds = [], onMapCoordinate = () => {},
  onOpenPhysical = null }) {
  const [layers, setLayers] = useState({ traffic: true, vehicles: true, events: true, labels: true })
  const [zoom, setZoom] = useState(16)
  const [mapCenter, setMapCenter] = useState(null)
  const intersectionById = useMemo(
    () => new Map(intersections.map((intersection) => [intersection.id, intersection])),
    [intersections],
  )
  const positions = useMemo(() => intersections.map(positionFor), [intersections])
  const roadById = useMemo(() => new Map(roads.map((road) => [road.id, road])), [roads])
  const routeRoadIds = useMemo(() => new Set((route?.steps || []).map((step) => step.roadId)), [route])
  const highlightedRoadSet = useMemo(() => new Set(highlightedRoadIds), [highlightedRoadIds])
  const denseNetwork = intersections.length > 80

  const toggleLayer = (name) => setLayers((current) => ({ ...current, [name]: !current[name] }))

  return (
    <section className={`map-shell ${captureIntersection ? 'coordinate-pick-mode' : ''}`} aria-label="Macro traffic network map">
      <div className="map-toolbar">
        <div className="map-overview-card">
          <span className="eyebrow">Network overview</span>
          <strong>{intersections.length} junctions · {roads.length} directed roads</strong>
          <span className="map-mode-note">Macro view · live dots only · sprites disabled</span>
        </div>
        <div className="layer-toggles" aria-label="Map layers">
          {Object.entries(layers).map(([name, visible]) => (
            <button key={name} className={visible ? 'active' : ''} aria-pressed={visible}
              onClick={() => toggleLayer(name)}>
              {name}
            </button>
          ))}
          {onOpenPhysical && (
            <button
              className="physical-open"
              disabled={zoom < 18}
              title={zoom < 18 ? 'Zoom in to lane level to open the physical view' : 'Open the lane-level physical view here'}
              onClick={() => {
                const target = physicalTargetFrom(selected, mapCenter)
                if (target) onOpenPhysical(target)
              }}>
              Open physical view
            </button>
          )}
        </div>
      </div>

      <MapContainer
        center={HCMC_CENTER}
        zoom={16}
        minZoom={16}
        maxZoom={19}
        maxBounds={HCMC_DISTRICT_BOUNDS}
        maxBoundsViscosity={1}
        preferCanvas
        className="traffic-map"
      >
        <TileLayer attribution={TILE_ATTRIBUTION} url={TILE_URL} />
        <FitNetwork positions={positions} />
        <MapZoomTracker onZoom={setZoom} onCenter={setMapCenter} />
        <MapCoordinatePicker active={captureIntersection} onPick={onMapCoordinate} />

        <Pane name="roads" style={{ zIndex: 410 }}>
          {roads.map((road) => {
            const start = intersectionById.get(road.startIntersectionId)
            const end = intersectionById.get(road.endIntersectionId)
            if (!start || !end) return null
            const isRoute = routeRoadIds.has(road.id)
            const isHighlighted = highlightedRoadSet.has(road.id)
            const classStyle = roadClassStyle(road.roadClass, zoom)
            const physicalWidthMetres = Number(road.roadWidthMetres
              || Number(road.laneCount || 1) * Number(road.laneWidthMetres || 3))
            return (
              <Polyline
                key={road.id}
                positions={positionsForRoad(road, start, end)}
                pathOptions={{
                  color: isHighlighted ? '#00a9c7' : layers.traffic ? roadColor(road) : '#77838f',
                  weight: isHighlighted ? 9 : isRoute ? 10
                    : classStyle.weight + Math.max(0, physicalWidthMetres - 3) * 0.35
                      + Math.max(0, Number(road.vehicleDensity || 0)) * 1.25,
                  opacity: isHighlighted ? 1 : isRoute ? 0.35 : classStyle.opacity,
                  dashArray: road.status === 'BLOCKED' || road.status === 'CLOSED' ? '8 7' : undefined,
                  lineCap: 'round',
                }}
                eventHandlers={{ click: () => onSelect({ kind: 'road', data: road }) }}
              >
                <Tooltip sticky>{road.id} · {road.laneCount || 1} lanes · {physicalWidthMetres.toFixed(1)} m wide · {road.status} · {(Number(road.congestionLevel || 0) * 100).toFixed(0)}%</Tooltip>
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
          {intersections.map((intersection) => {
            const isSelected = selected?.kind === 'intersection' && selected.data.id === intersection.id
            if (denseNetwork && zoom < 17 && !isSelected) return null
            return (
            <CircleMarker
              key={intersection.id}
              center={positionFor(intersection)}
              radius={isSelected ? 9 : denseNetwork ? Math.min(4, zoom - 15) : 7}
              pathOptions={{ color: '#ffffff', weight: denseNetwork ? 1 : 3, fillColor: '#172631', fillOpacity: 1 }}
              eventHandlers={{
                click: () => {
                  onSelect({ kind: 'intersection', data: intersection })
                  if (!captureIntersection) onPickIntersection(intersection.id)
                },
              }}
            >
              {layers.labels && (!denseNetwork || zoom >= 19)
                ? <Tooltip permanent direction="top" offset={[0, -8]}>{intersection.id}</Tooltip> : null}
              <Popup><strong>{intersection.name}</strong><br />{intersection.locationType}</Popup>
            </CircleMarker>
            )
          })}
        </Pane>

        {layers.vehicles ? (
          <Pane name="vehicles" style={{ zIndex: 460 }}>
            {vehicles.map((vehicle) => {
              if (vehicle.status === 'ARRIVED') return null
              const position = positionForVehicle(vehicle, roadById, intersectionById)
              if (!position) return null
              return (
                <Marker key={vehicle.id} position={position} icon={vehicleDotIcon(vehicle, zoom)}
                  eventHandlers={{ click: () => onSelect({ kind: 'vehicle', data: vehicle }) }}>
                  <Tooltip direction="top" offset={[0, -5]}>
                    {vehicle.id} · {vehicle.type} · lane {Number(vehicle.laneIndex || 0) + 1}
                    {' · '}{Math.round(Number(vehicle.instantaneousSpeed || 0))} km/h
                  </Tooltip>
                </Marker>
              )
            })}
          </Pane>
        ) : null}

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
            <Marker key={event.id} position={positionAlongPath(path, Number(event.positionAlongRoad ?? 0.5))}
              icon={divIcon(`event-pin ${event.status.toLowerCase()}`, eventGlyph[event.type] || '!', 30)}
              eventHandlers={{ click: () => onSelect({ kind: 'event', data: event }) }}>
              <Tooltip>{event.type.replaceAll('_', ' ')} · {event.status}</Tooltip>
            </Marker>
          )
        }) : null}

      </MapContainer>
      <MapLegend />
    </section>
  )
}

export default memo(TrafficMap)
