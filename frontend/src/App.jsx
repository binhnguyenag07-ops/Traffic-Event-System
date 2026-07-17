import { useMemo, useState } from 'react'
import TrafficMap from './components/TrafficMap'
import NetworkEditor from './components/NetworkEditor'
import { useTrafficDashboard } from './hooks/useTrafficDashboard'

const PREFERENCES = [
  ['CONGESTION_AWARE', 'Traffic aware'],
  ['SHORTEST_TIME', 'Fastest'],
  ['SHORTEST_DISTANCE', 'Shortest'],
  ['MINIMUM_HOPS', 'Fewest roads'],
]

const EVENT_TYPES = [
  ['CONGESTION', 'Congestion'],
  ['ACCIDENT', 'Accident'],
  ['ROAD_CLOSURE', 'Road closure'],
  ['TRAFFIC_LIGHT_DELAY', 'Signal delay'],
]

function Metric({ label, value, tone = '' }) {
  return (
    <div className={`metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Field({ label, children }) {
  return <label className="field"><span>{label}</span>{children}</label>
}

function Inspector({ selected }) {
  if (!selected) {
    return <p className="empty-copy">Select a road, junction, vehicle, or event on the map to inspect it.</p>
  }
  const entries = Object.entries(selected.data)
    .filter(([, value]) => value !== null && typeof value !== 'object')
    .slice(0, 9)
  return (
    <div className="inspector-grid">
      <span className="object-kind">{selected.kind}</span>
      {entries.map(([key, value]) => (
        <div key={key}><span>{key.replaceAll(/([A-Z])/g, ' $1')}</span><strong>{String(value)}</strong></div>
      ))}
    </div>
  )
}

export default function App() {
  const dashboard = useTrafficDashboard()
  const {
    intersections, roads, scenarios, activeScenario, snapshot, statistics, algorithms,
    route, comparison, selected, loading, error, tickDelay, streamConnected,
  } = dashboard
  const [routeStart, setRouteStart] = useState('')
  const [routeEnd, setRouteEnd] = useState('')
  const [preference, setPreference] = useState('CONGESTION_AWARE')
  const [algorithm, setAlgorithm] = useState('')
  const [vehicleType, setVehicleType] = useState('CAR')
  const [vehicleStart, setVehicleStart] = useState('')
  const [vehicleEnd, setVehicleEnd] = useState('')
  const [eventType, setEventType] = useState('CONGESTION')
  const [eventRoad, setEventRoad] = useState('')
  const [eventDuration, setEventDuration] = useState(8)
  const [captureIntersection, setCaptureIntersection] = useState(false)
  const [draftPoint, setDraftPoint] = useState(null)
  const [highlightedRoadIds, setHighlightedRoadIds] = useState([])

  const traffic = statistics.traffic || {}
  const activeEvents = snapshot.trafficEvents?.filter((event) => event.status === 'ACTIVE') || []
  const canStart = snapshot.status === 'READY' && snapshot.vehicles.length > 0
  const canPause = snapshot.status === 'RUNNING'
  const canResume = snapshot.status === 'PAUSED'
  const pressure = Math.round(Number(traffic.averageCongestionLevel || 0) * 100)
  const scenarioName = scenarios.find((scenario) => scenario.id === activeScenario)?.name || 'Small City'

  const algorithmOptions = useMemo(
    () => algorithms.map((item) => item.algorithmName || item.name).filter(Boolean),
    [algorithms],
  )

  const safely = (action) => action().catch(() => {})
  const pickIntersection = (id) => {
    if (!routeStart || routeEnd) {
      setRouteStart(id)
      setRouteEnd('')
    } else {
      setRouteEnd(id)
    }
  }

  const findRoute = () => {
    if (!routeStart || !routeEnd) return
    safely(() => dashboard.findRoute(routeStart, routeEnd, preference, algorithm || undefined))
  }

  const addVehicle = () => {
    if (!vehicleStart || !vehicleEnd) return
    safely(async () => {
      await dashboard.createVehicle(vehicleType, vehicleStart, vehicleEnd)
      setVehicleStart('')
      setVehicleEnd('')
    })
  }

  const addEvent = () => {
    if (!eventRoad) return
    const value = eventType === 'TRAFFIC_LIGHT_DELAY' ? 3 : eventType === 'ROAD_CLOSURE' ? undefined : 0.55
    safely(async () => {
      await dashboard.createEvent({
        type: eventType,
        affectedRoadId: eventRoad,
        startTick: snapshot.tick + 1,
        durationTicks: Number(eventDuration),
        value,
      })
      setEventRoad('')
    })
  }

  return (
    <main className="control-room">
      <header className="topbar">
        <div className="brand-block">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <div><span className="eyebrow">Urban traffic simulator</span><h1>Flow control</h1></div>
        </div>
        <div className="live-strip" role="status" aria-live="polite">
          <span className={`connection-dot ${streamConnected ? 'connected' : ''}`} />
          <span>{streamConnected ? 'Live stream' : 'Reconnecting'}</span>
          <strong>{snapshot.status}</strong>
          <b>T+{String(snapshot.tick).padStart(4, '0')}</b>
        </div>
        <div className="scenario-switcher">
          <span>Scenario</span>
          <select value={activeScenario} disabled={loading}
            onChange={(event) => dashboard.activateScenario(event.target.value)}>
            {scenarios.map((scenario) => <option key={scenario.id} value={scenario.id}>{scenario.name}</option>)}
          </select>
        </div>
      </header>

      {error ? <div className="global-error" role="alert"><strong>Connection issue</strong><span>{error}</span>
        <button onClick={dashboard.refreshAll}>Retry</button></div> : null}

      <section className="workspace">
        <div className="map-stage">
          <TrafficMap
            intersections={intersections}
            roads={roads}
            vehicles={snapshot.vehicles || []}
            events={snapshot.trafficEvents || []}
            route={route}
            selected={selected}
            onSelect={dashboard.setSelected}
            onPickIntersection={pickIntersection}
            captureIntersection={captureIntersection}
            draftIntersection={draftPoint}
            highlightedRoadIds={highlightedRoadIds}
            onMapCoordinate={(point) => {
              setDraftPoint(point)
              setCaptureIntersection(false)
            }}
          />
          <div className="map-metrics" aria-label="Live traffic summary">
            <Metric label="Fleet" value={snapshot.vehicles?.length || 0} />
            <Metric label="Network pressure" value={`${pressure}%`} tone={pressure > 65 ? 'danger' : ''} />
            <Metric label="Active incidents" value={activeEvents.length} tone={activeEvents.length ? 'warning' : ''} />
            <Metric label="Arrived" value={traffic.arrivedVehicles || 0} />
          </div>
        </div>

        <aside className="command-rail">
          <section className="rail-section simulation-section">
            <div className="section-heading"><div><span className="eyebrow">Playback</span><h2>{scenarioName}</h2></div>
              <span className={`status-chip ${snapshot.status.toLowerCase()}`}>{snapshot.status}</span></div>
            <div className="transport-controls">
              <button className="primary" disabled={!canStart || loading} onClick={() => safely(dashboard.start)}>Start</button>
              <button disabled={!canPause || loading} onClick={() => safely(dashboard.pause)}>Pause</button>
              <button disabled={!canResume || loading} onClick={() => safely(dashboard.resume)}>Resume</button>
              <button disabled={snapshot.status === 'RUNNING' || loading} onClick={() => safely(dashboard.advanceTick)}>Step</button>
              <button className="quiet" disabled={loading} onClick={() => safely(dashboard.reset)}>Reset</button>
            </div>
            <div className="setup-row">
              <button onClick={() => safely(dashboard.initializeDemo)} disabled={loading || snapshot.status === 'RUNNING'}>
                Load demo fleet
              </button>
              <Field label="Playback">
                <select value={tickDelay} onChange={(event) => safely(() => dashboard.setPlaybackInterval(Number(event.target.value)))}>
                  <option value="2000">0.5×</option><option value="1000">1×</option>
                  <option value="500">2×</option><option value="250">4×</option>
                </select>
              </Field>
            </div>
          </section>

          <details className="rail-section" open>
            <summary><span>Route lab</span><b>{route ? route.algorithm : 'Plan a trip'}</b></summary>
            <div className="section-body">
              <div className="two-fields">
                <Field label="From"><select value={routeStart} onChange={(e) => setRouteStart(e.target.value)}><option value="">Choose</option>{intersections.map((i) => <option key={i.id} value={i.id}>{i.id} · {i.name}</option>)}</select></Field>
                <Field label="To"><select value={routeEnd} onChange={(e) => setRouteEnd(e.target.value)}><option value="">Choose</option>{intersections.map((i) => <option key={i.id} value={i.id}>{i.id} · {i.name}</option>)}</select></Field>
              </div>
              <div className="two-fields">
                <Field label="Preference"><select value={preference} onChange={(e) => setPreference(e.target.value)}>{PREFERENCES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
                <Field label="Algorithm"><select value={algorithm} onChange={(e) => setAlgorithm(e.target.value)}><option value="">Automatic</option>{algorithmOptions.map((name) => <option key={name}>{name}</option>)}</select></Field>
              </div>
              <div className="action-row"><button className="primary" onClick={findRoute} disabled={!routeStart || !routeEnd}>Show route</button>
                <button onClick={() => safely(() => dashboard.compareRoutes(routeStart, routeEnd, preference))} disabled={!routeStart || !routeEnd}>Compare all</button></div>
              {route ? <div className="route-readout"><Metric label="Distance" value={`${route.totalDistance?.toFixed(1)} km`} /><Metric label="Travel time" value={`${(route.estimatedTravelTime * 60).toFixed(1)} min`} /><Metric label="Visited" value={route.visitedNodes} /></div> : null}
              {comparison.length ? <div className="comparison-list">{comparison.map((item) => <div key={item.algorithm}><strong>{item.algorithm}</strong><span>{item.success ? `${item.result.computationTimeMillis} ms · ${item.result.visitedNodes} nodes` : item.error}</span></div>)}</div> : null}
            </div>
          </details>

          <details className="rail-section">
            <summary><span>Fleet</span><b>{snapshot.vehicles?.length || 0} vehicles</b></summary>
            <div className="section-body">
              <div className="three-fields">
                <Field label="Type"><select value={vehicleType} onChange={(e) => setVehicleType(e.target.value)}><option>CAR</option><option>BUS</option><option>EMERGENCY</option></select></Field>
                <Field label="Start"><select value={vehicleStart} onChange={(e) => setVehicleStart(e.target.value)}><option value="">Choose</option>{intersections.map((i) => <option key={i.id}>{i.id}</option>)}</select></Field>
                <Field label="Destination"><select value={vehicleEnd} onChange={(e) => setVehicleEnd(e.target.value)}><option value="">Choose</option>{intersections.map((i) => <option key={i.id}>{i.id}</option>)}</select></Field>
              </div>
              <button className="primary wide" onClick={addVehicle} disabled={!vehicleStart || !vehicleEnd}>Add vehicle</button>
              <div className="entity-list">{snapshot.vehicles?.map((vehicle) => <button key={vehicle.id} onClick={() => dashboard.setSelected({ kind: 'vehicle', data: vehicle })}><i className={`entity-dot ${vehicle.status.toLowerCase()}`} /><span><strong>{vehicle.id}</strong><small>{vehicle.type} · {vehicle.status}</small></span><b>{Math.round((vehicle.roadProgress || 0) * 100)}%</b></button>)}</div>
            </div>
          </details>

          <NetworkEditor
            dashboard={dashboard}
            intersections={intersections}
            status={snapshot.status}
            selected={selected}
            onSelect={dashboard.setSelected}
            captureIntersection={captureIntersection}
            setCaptureIntersection={setCaptureIntersection}
            draftPoint={draftPoint}
            setDraftPoint={setDraftPoint}
            onHighlightsChange={setHighlightedRoadIds}
          />

          <details className="rail-section">
            <summary><span>Incidents</span><b>{activeEvents.length} active</b></summary>
            <div className="section-body">
              <div className="three-fields">
                <Field label="Type"><select value={eventType} onChange={(e) => setEventType(e.target.value)}>{EVENT_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
                <Field label="Road"><select value={eventRoad} onChange={(e) => setEventRoad(e.target.value)}><option value="">Choose</option>{roads.map((road) => <option key={road.id}>{road.id}</option>)}</select></Field>
                <Field label="Ticks"><input type="number" min="1" max="50" value={eventDuration} onChange={(e) => setEventDuration(e.target.value)} /></Field>
              </div>
              <button className="primary wide" onClick={addEvent} disabled={!eventRoad}>Schedule incident</button>
            </div>
          </details>

          <details className="rail-section">
            <summary><span>Inspector</span><b>{selected ? selected.kind : 'Nothing selected'}</b></summary>
            <div className="section-body"><Inspector selected={selected} /></div>
          </details>
        </aside>
      </section>

      <footer className="tick-rail">
        <div><span>Simulation timeline</span><strong>Tick {snapshot.tick}</strong></div>
        <div className="timeline-track"><i style={{ width: `${Math.min(100, (snapshot.tick % 100))}%` }} /></div>
        <span>{roads.length} roads</span><span>{intersections.length} junctions</span><span>{activeEvents.length} incidents</span>
      </footer>
    </main>
  )
}
