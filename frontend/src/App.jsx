import { useState } from 'react'
import { useRoadNetwork } from './hooks/useRoadNetwork'
import { useSimulation } from './hooks/useSimulation'
import { useStatistics } from './hooks/useStatistics'
import MapViewport from './components/MapViewport'
import ControlPanel from './components/ControlPanel'
import VehiclePanel from './components/VehiclePanel'
import EventPanel from './components/EventPanel'
import AlgorithmPanel from './components/AlgorithmPanel'
import StatisticsPanel from './components/StatisticsPanel'
import {
  ROAD_STATUS_COLOR,
  VEHICLE_STATUS_COLOR,
  LOCATION_TYPE_COLOR,
} from './theme'

function SelectedDetails({ selected, intersections, roads, vehicles, onClear }) {
  if (!selected) return null

  let body = null
  if (selected.type === 'intersection') {
    const i = intersections.find((x) => x.id === selected.id)
    if (i) {
      body = (
        <>
          <h3>
            Intersection <code>{i.id}</code>
          </h3>
          <p>{i.name}</p>
          <p>
            Type:{' '}
            <span className="v-status" style={{ color: LOCATION_TYPE_COLOR[i.locationType] ?? '#64748b' }}>
              {i.locationType}
            </span>
          </p>
          <p>Position: ({i.x}, {i.y})</p>
        </>
      )
    }
  } else if (selected.type === 'road') {
    const r = roads.find((x) => x.id === selected.id)
    if (r) {
      body = (
        <>
          <h3>
            Road <code>{r.id}</code>
          </h3>
          <p>
            <code>{r.startIntersectionId}</code> → <code>{r.endIntersectionId}</code>
          </p>
          <p>
            Status:{' '}
            <span className="v-status" style={{ color: ROAD_STATUS_COLOR[r.status] ?? '#64748b' }}>
              {r.status}
            </span>
          </p>
          <p>Distance: {r.distance?.toFixed(1)} km · Speed limit: {r.speedLimit} km/h</p>
          <p>Vehicles: {r.currentVehicleCount} / {r.capacity}</p>
          <p>Congestion: {((r.congestionLevel ?? 0) * 100).toFixed(1)}%</p>
        </>
      )
    }
  } else if (selected.type === 'vehicle') {
    const v = vehicles.find((x) => x.id === selected.id)
    if (v) {
      const totalSteps = v.routeSteps?.length ?? 0
      body = (
        <>
          <h3>
            Vehicle <code>{v.id}</code>
          </h3>
          <p>Type: {v.type}</p>
          <p>
            <code>{v.currentIntersectionId}</code> → <code>{v.destinationIntersectionId}</code>
          </p>
          <p>
            Status:{' '}
            <span className="v-status" style={{ color: VEHICLE_STATUS_COLOR[v.status] ?? '#64748b' }}>
              {v.status}
            </span>
          </p>
          {totalSteps > 0 && (
            <p>
              Route progress: {Math.min(v.nextRouteStepIndex ?? 0, totalSteps)} / {totalSteps} steps
            </p>
          )}
          {v.occupiedRoadId && (
            <p>
              On road: <code>{v.occupiedRoadId}</code>
            </p>
          )}
          {(v.travelHistory?.length ?? 0) > 1 && (
            <p>Visited: {v.travelHistory.join(' → ')}</p>
          )}
        </>
      )
    }
  }

  if (!body) return null

  return (
    <section className="sim-panel selected-details">
      <div className="selected-details-head">
        <h2>Details</h2>
        <button className="sim-btn-xs red" onClick={onClear} aria-label="Clear selection">
          Close
        </button>
      </div>
      {body}
    </section>
  )
}

export default function App() {
  const network = useRoadNetwork()
  const sim = useSimulation()
  const stats = useStatistics(sim.tick)
  const [route, setRoute] = useState(null)
  const [selected, setSelected] = useState(null)

  // Shared, tick-consistent state: once the simulation produces snapshots the
  // road state and statistics come from them; before that, fall back to the
  // network/statistics APIs so the dashboard renders on first load.
  const roads = sim.liveRoads.length > 0 ? sim.liveRoads : network.roads
  const traffic = sim.statistics ?? stats.traffic

  return (
    <div className="app app-wide">
      <header className="app-header">
        <h1>Traffic Simulation Dashboard</h1>
        <p className="subtitle">
          {network.summary
            ? `${network.summary.intersectionCount} intersections · ${network.summary.roadCount} roads`
            : 'Connecting to backend…'}
        </p>
      </header>

      {network.error && (
        <div className="error-box">
          <strong>Network error:</strong> {network.error}
          {'\n'}Is the backend running? Start it with: mvn exec:java
        </div>
      )}

      <main className="dashboard">
        <div className="dash-main">
          <MapViewport
            intersections={network.intersections}
            roads={roads}
            vehicles={sim.vehicles}
            events={sim.trafficEvents}
            route={route}
            selected={selected}
            onSelect={setSelected}
          />
          <SelectedDetails
            selected={selected}
            intersections={network.intersections}
            roads={roads}
            vehicles={sim.vehicles}
            onClear={() => setSelected(null)}
          />
        </div>

        <aside className="dash-side">
          <ControlPanel sim={sim} intersections={network.intersections} />
        </aside>
      </main>

      <div className="dash-grid">
        <VehiclePanel vehicles={sim.vehicles} selected={selected} onSelect={setSelected} />
        <EventPanel roads={roads} tick={sim.tick} onChanged={sim.refreshSnapshot} />
        <AlgorithmPanel intersections={network.intersections} onRouteFound={setRoute} />
        <StatisticsPanel
          traffic={traffic}
          algorithms={stats.algorithms}
          roads={roads}
          loading={stats.loading}
          error={stats.error}
          onRefresh={() => {
            stats.refresh()
            sim.refreshSnapshot().catch(() => {})
          }}
        />
      </div>
    </div>
  )
}
