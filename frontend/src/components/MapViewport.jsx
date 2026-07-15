import { useMemo } from 'react'
import {
  ROAD_STATUS_COLOR,
  VEHICLE_STATUS_COLOR,
  EVENT_TYPE_COLOR,
  LOCATION_TYPE_COLOR,
} from '../theme'

const SCALE = 140
const PAD = 70
const ROAD_OFFSET = 6 // separates the two directions of a road pair

/**
 * SVG map of the road network.
 *
 * Props contract (agreed integration point for all four tracks):
 *   intersections  [{ id, name, x, y, locationType }]          — Binh's network
 *   roads          [{ id, startIntersectionId, endIntersectionId,
 *                     status, congestionLevel, ... }]           — Binh's network / snapshot
 *   vehicles       [{ id, type, currentIntersectionId, status }] — Hyo's vehicles (snapshot)
 *   events         [{ id, type, affectedRoadId, status }]        — Hyo's events (snapshot)
 *   route          { steps: [{ from, to, roadId }] } | null      — Nguyie's route result
 *   selected       { type: 'intersection'|'road'|'vehicle', id } | null
 *   onSelect       (selection | null) => void
 */
export default function MapViewport({
  intersections = [],
  roads = [],
  vehicles = [],
  events = [],
  route = null,
  selected = null,
  onSelect = () => {},
}) {
  const positions = useMemo(() => {
    const map = {}
    if (intersections.length === 0) return { map, width: 0, height: 0 }
    const xs = intersections.map((i) => i.x ?? 0)
    const ys = intersections.map((i) => i.y ?? 0)
    const minX = Math.min(...xs)
    const minY = Math.min(...ys)
    intersections.forEach((i) => {
      map[i.id] = {
        x: ((i.x ?? 0) - minX) * SCALE + PAD,
        y: ((i.y ?? 0) - minY) * SCALE + PAD,
      }
    })
    const width = (Math.max(...xs) - minX) * SCALE + PAD * 2
    const height = (Math.max(...ys) - minY) * SCALE + PAD * 2
    return { map, width, height }
  }, [intersections])

  const routeRoadIds = useMemo(
    () => new Set((route?.steps ?? []).map((s) => s.roadId)),
    [route]
  )

  const activeEvents = events.filter((ev) => ev.status === 'ACTIVE')

  const vehiclesByIntersection = useMemo(() => {
    const grouped = {}
    vehicles.forEach((v) => {
      const at = v.currentIntersectionId
      if (!grouped[at]) grouped[at] = []
      grouped[at].push(v)
    })
    return grouped
  }, [vehicles])

  if (intersections.length === 0) {
    return (
      <section className="sim-panel map-panel">
        <h2>Network Map</h2>
        <p className="sim-hint">No network loaded yet.</p>
      </section>
    )
  }

  // Offsets a road line perpendicular to its direction so that the two
  // directions of a road pair (A→B and B→A) render side by side.
  const roadLine = (road) => {
    const from = positions.map[road.startIntersectionId]
    const to = positions.map[road.endIntersectionId]
    if (!from || !to) return null
    const dx = to.x - from.x
    const dy = to.y - from.y
    const len = Math.sqrt(dx * dx + dy * dy) || 1
    const ox = (-dy / len) * ROAD_OFFSET
    const oy = (dx / len) * ROAD_OFFSET
    return {
      x1: from.x + ox,
      y1: from.y + oy,
      x2: to.x + ox,
      y2: to.y + oy,
      midX: (from.x + to.x) / 2 + ox * 2,
      midY: (from.y + to.y) / 2 + oy * 2,
    }
  }

  const roadLines = {}
  roads.forEach((r) => {
    const line = roadLine(r)
    if (line) roadLines[r.id] = line
  })

  const routePoints = (route?.steps ?? [])
    .map((step, i) => {
      const from = positions.map[step.from]
      const to = positions.map[step.to]
      if (!from || !to) return null
      return i === 0 ? [from, to] : [to]
    })
    .filter(Boolean)
    .flat()

  return (
    <section className="sim-panel map-panel">
      <h2>Network Map</h2>
      <svg
        data-testid="map-svg"
        className="map-svg"
        viewBox={`0 0 ${positions.width} ${positions.height}`}
        role="img"
        aria-label="Road network map"
        onClick={() => onSelect(null)}
      >
        {/* Roads */}
        {roads.map((r) => {
          const line = roadLines[r.id]
          if (!line) return null
          const isSelected = selected?.type === 'road' && selected.id === r.id
          const inRoute = routeRoadIds.has(r.id)
          return (
            <line
              key={r.id}
              data-testid={`map-road-${r.id}`}
              data-status={r.status}
              data-in-route={inRoute ? 'true' : 'false'}
              x1={line.x1}
              y1={line.y1}
              x2={line.x2}
              y2={line.y2}
              stroke={ROAD_STATUS_COLOR[r.status] ?? '#94a3b8'}
              strokeWidth={isSelected ? 8 : 4 + (r.congestionLevel ?? 0) * 4}
              strokeLinecap="round"
              opacity={isSelected ? 1 : 0.85}
              style={{ cursor: 'pointer' }}
              onClick={(e) => {
                e.stopPropagation()
                onSelect({ type: 'road', id: r.id })
              }}
            >
              <title>{`${r.id}: ${r.startIntersectionId} to ${r.endIntersectionId} (${r.status})`}</title>
            </line>
          )
        })}

        {/* Route highlight overlay */}
        {routePoints.length > 1 && (
          <polyline
            data-testid="map-route-overlay"
            points={routePoints.map((p) => `${p.x},${p.y}`).join(' ')}
            fill="none"
            stroke="#3b82f6"
            strokeWidth={10}
            strokeLinecap="round"
            strokeLinejoin="round"
            opacity={0.45}
            pointerEvents="none"
          />
        )}

        {/* Active event badges at road midpoints */}
        {activeEvents.map((ev) => {
          const line = roadLines[ev.affectedRoadId]
          if (!line) return null
          return (
            <g key={ev.id} data-testid={`map-event-${ev.id}`} pointerEvents="none">
              <circle
                cx={line.midX}
                cy={line.midY}
                r={9}
                fill={EVENT_TYPE_COLOR[ev.type] ?? '#64748b'}
                stroke="#fff"
                strokeWidth={2}
              />
              <text
                x={line.midX}
                y={line.midY + 4}
                textAnchor="middle"
                fontSize="10"
                fontWeight="700"
                fill="#fff"
              >
                !
              </text>
            </g>
          )
        })}

        {/* Intersections */}
        {intersections.map((i) => {
          const pos = positions.map[i.id]
          const isSelected = selected?.type === 'intersection' && selected.id === i.id
          return (
            <g
              key={i.id}
              data-testid={`map-intersection-${i.id}`}
              style={{ cursor: 'pointer' }}
              onClick={(e) => {
                e.stopPropagation()
                onSelect({ type: 'intersection', id: i.id })
              }}
            >
              {isSelected && (
                <circle cx={pos.x} cy={pos.y} r={16} fill="none" stroke="#3b82f6" strokeWidth={3} />
              )}
              <circle
                cx={pos.x}
                cy={pos.y}
                r={10}
                fill={LOCATION_TYPE_COLOR[i.locationType] ?? '#64748b'}
                stroke="#fff"
                strokeWidth={2}
              />
              <text
                x={pos.x}
                y={pos.y - 16}
                textAnchor="middle"
                fontSize="12"
                fontWeight="700"
                fill="#1e293b"
              >
                {i.id}
              </text>
              <text x={pos.x} y={pos.y + 26} textAnchor="middle" fontSize="10" fill="#64748b">
                {i.name}
              </text>
            </g>
          )
        })}

        {/* Vehicles, spread around their current intersection */}
        {Object.entries(vehiclesByIntersection).map(([intersectionId, group]) =>
          group.map((v, idx) => {
            const pos = positions.map[intersectionId]
            if (!pos) return null
            const angle = (idx / group.length) * Math.PI * 2
            const vx = pos.x + Math.cos(angle) * 22
            const vy = pos.y + Math.sin(angle) * 22
            const isSelected = selected?.type === 'vehicle' && selected.id === v.id
            return (
              <g
                key={v.id}
                data-testid={`map-vehicle-${v.id}`}
                data-at={intersectionId}
                style={{ cursor: 'pointer' }}
                onClick={(e) => {
                  e.stopPropagation()
                  onSelect({ type: 'vehicle', id: v.id })
                }}
              >
                {isSelected && (
                  <circle cx={vx} cy={vy} r={12} fill="none" stroke="#3b82f6" strokeWidth={2.5} />
                )}
                <circle
                  cx={vx}
                  cy={vy}
                  r={8}
                  fill={VEHICLE_STATUS_COLOR[v.status] ?? '#64748b'}
                  stroke="#fff"
                  strokeWidth={1.5}
                />
                <text
                  x={vx}
                  y={vy + 3.5}
                  textAnchor="middle"
                  fontSize="9"
                  fontWeight="700"
                  fill="#fff"
                >
                  {(v.type ?? '?').charAt(0)}
                </text>
                <title>{`${v.id} (${v.type}) at ${intersectionId} - ${v.status}`}</title>
              </g>
            )
          })
        )}
      </svg>

      <div className="map-legend">
        <span className="legend-item">
          <span className="legend-swatch" style={{ background: ROAD_STATUS_COLOR.OPEN }} /> Open
        </span>
        <span className="legend-item">
          <span className="legend-swatch" style={{ background: ROAD_STATUS_COLOR.CONGESTED }} /> Congested
        </span>
        <span className="legend-item">
          <span className="legend-swatch" style={{ background: ROAD_STATUS_COLOR.BLOCKED }} /> Blocked
        </span>
        <span className="legend-item">
          <span className="legend-swatch" style={{ background: ROAD_STATUS_COLOR.CLOSED }} /> Closed
        </span>
        <span className="legend-item">
          <span className="legend-swatch round" style={{ background: '#3b82f6' }} /> Route
        </span>
        <span className="legend-item">
          <span className="legend-swatch round" style={{ background: VEHICLE_STATUS_COLOR.MOVING }} /> Vehicle
        </span>
      </div>
    </section>
  )
}
