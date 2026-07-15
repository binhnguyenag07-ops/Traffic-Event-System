import { useState, useEffect, useRef } from 'react'
import { simulationApi, routingApi, trafficEventApi, networkApi, statisticsApi } from './api/trafficAPI'
import VehicleAsset from './vehicle-assets/VehicleAsset'

const PREFERENCES = [
  { value: 'SHORTEST_TIME', label: 'Shortest Time' },
  { value: 'SHORTEST_DISTANCE', label: 'Shortest Distance' },
  { value: 'CONGESTION_AWARE', label: 'Congestion Aware' },
  { value: 'MINIMUM_HOPS', label: 'Minimum Hops' },
]

const DEFAULT_VEHICLES = [
  { id: 'v1', type: 'CAR',       currentIntersectionId: 'I1', destinationIntersectionId: 'I6' },
  { id: 'v2', type: 'BUS',       currentIntersectionId: 'I2', destinationIntersectionId: 'I5' },
  { id: 'v3', type: 'EMERGENCY', currentIntersectionId: 'I3', destinationIntersectionId: 'I4' },
]

const STATUS_COLOR        = { READY: '#6366f1', RUNNING: '#16a34a', PAUSED: '#d97706' }
const VEHICLE_STATUS_COLOR = { MOVING: '#2563eb', ARRIVED: '#16a34a', BLOCKED: '#dc2626', WAITING: '#64748b' }
const ROAD_STATUS_COLOR   = { OPEN: '#16a34a', CONGESTED: '#d97706', BLOCKED: '#dc2626', CLOSED: '#991b1b' }
const EVENT_TYPES         = ['CONGESTION', 'ACCIDENT', 'ROAD_CLOSURE', 'TRAFFIC_LIGHT_DELAY']
const EVENT_TYPE_COLOR    = { CONGESTION: '#f59e0b', ACCIDENT: '#dc2626', ROAD_CLOSURE: '#991b1b', TRAFFIC_LIGHT_DELAY: '#6366f1' }
const EVENT_STATUS_COLOR  = { SCHEDULED: '#3b82f6', ACTIVE: '#d97706', RESOLVED: '#16a34a' }

// ── Network Panel (Week 2) ───────────────────────────────────────────────────

function NetworkPanel() {
  const [summary, setSummary] = useState(null)
  const [roads, setRoads]     = useState([])
  const [showRoads, setShowRoads] = useState(false)
  const [loading, setLoading] = useState(false)

  useEffect(() => {
    setLoading(true)
    Promise.all([networkApi.getRoadNetwork(), networkApi.getAllRoads()])
      .then(([net, rds]) => {
        setSummary(net)
        setRoads(Array.isArray(rds) ? rds : [])
      })
      .catch(() => {})
      .finally(() => setLoading(false))
  }, [])

  if (!summary && !loading) return null

  return (
    <section className="sim-panel">
      <h2>Road Network</h2>

      {loading && <p className="sim-hint">Loading network…</p>}

      {summary && (
        <>
          <div className="sim-status-bar">
            <span className="sim-badge" style={{ background: '#0ea5e9' }}>
              {summary.intersectionCount} intersections
            </span>
            <span className="sim-badge" style={{ background: '#8b5cf6' }}>
              {summary.roadCount} roads
            </span>
          </div>

          <div className="sim-row">
            <button className="sim-btn secondary" onClick={() => setShowRoads(v => !v)}>
              {showRoads ? 'Hide Roads' : 'Show Roads'}
            </button>
          </div>

          {showRoads && roads.length > 0 && (
            <div className="vehicle-table-wrap">
              <table className="steps-table">
                <thead>
                  <tr>
                    <th>ID</th><th>From → To</th><th>Dist (km)</th>
                    <th>Speed (km/h)</th><th>Vehicles / Cap</th><th>Status</th>
                  </tr>
                </thead>
                <tbody>
                  {roads.map(r => (
                    <tr key={r.id}>
                      <td><code>{r.id}</code></td>
                      <td><code>{r.startIntersectionId}</code> → <code>{r.endIntersectionId}</code></td>
                      <td>{r.distance?.toFixed(1)}</td>
                      <td>{r.speedLimit}</td>
                      <td>{r.currentVehicleCount} / {r.capacity}</td>
                      <td>
                        <span className="v-status" style={{ color: ROAD_STATUS_COLOR[r.status] ?? '#64748b' }}>
                          {r.status}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </>
      )}
    </section>
  )
}

// ── Simulation Panel (Week 4) ────────────────────────────────────────────────

function SimulationPanel() {
  const [simStatus, setSimStatus]         = useState('READY')
  const [currentTick, setCurrentTick]     = useState(0)
  const [vehicles, setVehicles]           = useState([])
  const [trafficEvents, setTrafficEvents] = useState([])
  const [initialized, setInitialized]     = useState(false)
  const [loading, setLoading]             = useState(false)
  const [error, setSimError]              = useState(null)
  const [autoTick, setAutoTick]           = useState(false)
  const [tickInterval, setTickInterval]   = useState(1000)
  const autoTickRef = useRef(null)

  const wrap = async (fn) => {
    setLoading(true)
    setSimError(null)
    try { await fn() } catch (e) { setSimError(e.message) } finally { setLoading(false) }
  }

  const refreshSnapshot = async () => {
    const snap = await simulationApi.getSimulationSnapshot()
    setCurrentTick(snap.tick ?? 0)
    setVehicles(snap.vehicles ?? [])
    setTrafficEvents(snap.trafficEvents ?? [])
  }

  const refreshStatus = async () => {
    const s = await simulationApi.getSimulationStatus()
    setSimStatus(s.status)
    setCurrentTick(s.currentTick ?? 0)
  }

  const handleInitialize = () => wrap(async () => {
    await simulationApi.initializeSimulation(DEFAULT_VEHICLES, [])
    await refreshSnapshot()
    await refreshStatus()
    setInitialized(true)
    setAutoTick(false)
  })

  const handleStart = () => wrap(async () => {
    await simulationApi.startSimulation()
    setSimStatus('RUNNING')
  })

  const handlePause = () => wrap(async () => {
    await simulationApi.pauseSimulation()
    setSimStatus('PAUSED')
    setAutoTick(false)
  })

  const handleResume = () => wrap(async () => {
    await simulationApi.resumeSimulation()
    setSimStatus('RUNNING')
  })

  const handleReset = () => wrap(async () => {
    await simulationApi.resetSimulation()
    setAutoTick(false)
    await refreshSnapshot()
    await refreshStatus()
  })

  const handleTick = () => wrap(async () => {
    const snap = await simulationApi.advanceOneTick()
    setCurrentTick(snap.tick ?? currentTick + 1)
    setVehicles(snap.vehicles ?? [])
    setTrafficEvents(snap.trafficEvents ?? [])
  })

  // Auto-tick effect
  useEffect(() => {
    if (autoTick && simStatus === 'RUNNING') {
      autoTickRef.current = setInterval(() => {
        simulationApi.advanceOneTick()
          .then((snap) => {
            setCurrentTick(snap.tick ?? 0)
            setVehicles(snap.vehicles ?? [])
            setTrafficEvents(snap.trafficEvents ?? [])
          })
          .catch((e) => {
            setSimError(e.message)
            setAutoTick(false)
          })
      }, tickInterval)
    }
    return () => clearInterval(autoTickRef.current)
  }, [autoTick, simStatus, tickInterval])

  const isRunning = simStatus === 'RUNNING'
  const isPaused  = simStatus === 'PAUSED'
  const isReady   = simStatus === 'READY'

  return (
    <section className="sim-panel">
      <h2>Simulation</h2>

      {/* Status bar */}
      <div className="sim-status-bar">
        <span className="sim-badge" style={{ background: STATUS_COLOR[simStatus] ?? '#64748b' }}>
          {simStatus}
        </span>
        <span className="sim-tick">Tick&nbsp;<strong>{currentTick}</strong></span>
      </div>

      {/* Initialize */}
      <div className="sim-row">
        <button className="sim-btn primary" onClick={handleInitialize} disabled={loading}>
          Initialize
        </button>
        <span className="sim-hint">Loads 3 default vehicles (CAR I1→I6, BUS I2→I5, EMERGENCY I3→I4)</span>
      </div>

      {/* State controls */}
      <div className="sim-row">
        {isReady && (
          <button className="sim-btn green" onClick={handleStart} disabled={!initialized || loading}>
            ▶ Start
          </button>
        )}
        {isRunning && (
          <button className="sim-btn amber" onClick={handlePause} disabled={loading}>
            ⏸ Pause
          </button>
        )}
        {isPaused && (
          <button className="sim-btn green" onClick={handleResume} disabled={loading}>
            ▶ Resume
          </button>
        )}
        <button className="sim-btn red" onClick={handleReset} disabled={!initialized || isReady || loading}>
          ↺ Reset
        </button>
      </div>

      {/* Tick controls */}
      <div className="sim-row">
        <button
          className="sim-btn secondary"
          onClick={handleTick}
          disabled={loading || isRunning}
          title="Advance 1 tick manually"
        >
          Step 1 Tick
        </button>

        <label className="auto-label">
          <input
            type="checkbox"
            checked={autoTick}
            onChange={(e) => setAutoTick(e.target.checked)}
            disabled={!isRunning}
          />
          &nbsp;Auto-tick every
        </label>
        <select
          value={tickInterval}
          onChange={(e) => setTickInterval(Number(e.target.value))}
          disabled={!isRunning}
          className="interval-select"
        >
          <option value={2000}>2 s</option>
          <option value={1000}>1 s</option>
          <option value={500}>0.5 s</option>
          <option value={250}>0.25 s</option>
        </select>
      </div>

      {error && <div className="sim-error">{error}</div>}

      {/* Vehicle table */}
      {vehicles.length > 0 && (
        <div className="vehicle-table-wrap">
          <h3>Vehicles</h3>
          <table className="steps-table">
            <thead>
              <tr>
                <th>ID</th><th>Type</th><th>Current</th><th>Destination</th><th>Status</th><th>Steps</th>
              </tr>
            </thead>
            <tbody>
              {vehicles.map((v) => (
                <tr key={v.id}>
                  <td><code>{v.id}</code></td>
                  <td>
                    <span className="vehicle-kind">
                      <VehicleAsset type={v.type} size={26} decorative />
                      <span>{v.type}</span>
                    </span>
                  </td>
                  <td><code>{v.currentIntersectionId}</code></td>
                  <td><code>{v.destinationIntersectionId}</code></td>
                  <td>
                    <span className="v-status" style={{ color: VEHICLE_STATUS_COLOR[v.status] ?? '#64748b' }}>
                      {v.status}
                    </span>
                  </td>
                  <td>{(v.travelHistory?.length ?? 1) - 1}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Active events table (from snapshot) */}
      {trafficEvents.length > 0 && (
        <div className="vehicle-table-wrap">
          <h3>Active Events</h3>
          <table className="steps-table">
            <thead>
              <tr><th>ID</th><th>Type</th><th>Road</th><th>Status</th></tr>
            </thead>
            <tbody>
              {trafficEvents.map((ev) => (
                <tr key={ev.id}>
                  <td><code>{ev.id}</code></td>
                  <td>
                    <span className="v-status" style={{ color: EVENT_TYPE_COLOR[ev.type] ?? '#64748b' }}>
                      {ev.type?.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td><code>{ev.affectedRoadId}</code></td>
                  <td>
                    <span className="v-status" style={{ color: EVENT_STATUS_COLOR[ev.status] ?? '#64748b' }}>
                      {ev.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}

// ── Event Controls Panel (Week 5) ────────────────────────────────────────────

function EventsPanel() {
  const [events, setEvents]               = useState([])
  const [roads, setRoads]                 = useState([])
  const [loading, setLoading]             = useState(false)
  const [error, setError]                 = useState(null)
  const [type, setType]                   = useState('CONGESTION')
  const [roadId, setRoadId]               = useState('')
  const [startTick, setStartTick]         = useState(0)
  const [durationTicks, setDurationTicks] = useState(5)
  const [value, setValue]                 = useState('')
  const [statusFilter, setStatusFilter]   = useState('ALL')
  const [roadFilter, setRoadFilter]       = useState('ALL')

  useEffect(() => {
    networkApi.getAllRoads()
      .then(rds => setRoads(Array.isArray(rds) ? rds : []))
      .catch(() => {})
  }, [])

  const fetchEvents = async (status = statusFilter, road = roadFilter) => {
    try {
      let data
      if (road !== 'ALL') {
        data = await trafficEventApi.getEventsOnRoad(road)
      } else if (status === 'ACTIVE') {
        data = await trafficEventApi.getActiveEvents()
      } else {
        data = await trafficEventApi.getAllEvents()
      }
      let list = Array.isArray(data) ? data : []
      if (status !== 'ALL') {
        list = list.filter(ev => ev.status === status)
      }
      setEvents(list)
    } catch (_) {}
  }

  useEffect(() => { fetchEvents() }, [statusFilter, roadFilter])

  const handleCreate = async () => {
    if (!roadId) { setError('Please select a road'); return }
    setLoading(true)
    setError(null)
    try {
      const eventData = value !== '' ? { value: parseFloat(value) } : {}
      await trafficEventApi.createEvent(
        type, roadId, Number(startTick), Number(durationTicks), eventData
      )
      await fetchEvents()
      setRoadId('')
      setValue('')
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const handleActivate = async (id) => {
    setError(null)
    try {
      await trafficEventApi.updateEventStatus(id, 'ACTIVE')
      await fetchEvents()
    } catch (e) {
      setError(e.message)
    }
  }

  const handleResolve = async (id) => {
    setError(null)
    try {
      await trafficEventApi.updateEventStatus(id, 'RESOLVED')
      await fetchEvents()
    } catch (e) {
      setError(e.message)
    }
  }

  const handleDelete = async (id) => {
    setError(null)
    try {
      await trafficEventApi.removeEvent(id)
      await fetchEvents()
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <section className="sim-panel">
      <h2>Event Controls</h2>

      <div className="form-row" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div className="form-group">
          <label>Event Type</label>
          <select value={type} onChange={e => setType(e.target.value)}>
            {EVENT_TYPES.map(t => (
              <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label>Road</label>
          <select value={roadId} onChange={e => setRoadId(e.target.value)}>
            <option value="">Select a road…</option>
            {roads.map(r => (
              <option key={r.id} value={r.id}>
                {r.id} ({r.startIntersectionId} → {r.endIntersectionId})
              </option>
            ))}
          </select>
        </div>
        <div className="form-group" style={{ flex: '0 0 90px' }}>
          <label>Start Tick</label>
          <input
            type="number"
            min={0}
            value={startTick}
            onChange={e => setStartTick(e.target.value)}
          />
        </div>
        <div className="form-group" style={{ flex: '0 0 90px' }}>
          <label>Duration</label>
          <input
            type="number"
            min={1}
            value={durationTicks}
            onChange={e => setDurationTicks(e.target.value)}
          />
        </div>
        <div className="form-group" style={{ flex: '0 0 90px' }}>
          <label>Value</label>
          <input
            type="number"
            min={0}
            step={0.1}
            value={value}
            onChange={e => setValue(e.target.value)}
            placeholder="0.5"
          />
        </div>
      </div>

      <div className="sim-row">
        <button className="sim-btn amber" onClick={handleCreate} disabled={loading}>
          + Dispatch Event
        </button>
        <button className="sim-btn secondary" onClick={() => fetchEvents()}>
          Refresh
        </button>
        <span className="sim-hint">
          Value: congestion increase (0–1), capacity reduction (0–1), or delay seconds
        </span>
      </div>

      <div className="sim-row">
        <div className="form-group" style={{ flex: '0 0 160px' }}>
          <label>Filter Status</label>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value)}>
            <option value="ALL">All</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="ACTIVE">Active</option>
            <option value="RESOLVED">Resolved</option>
          </select>
        </div>
        <div className="form-group" style={{ flex: '0 0 220px' }}>
          <label>Filter Road</label>
          <select value={roadFilter} onChange={e => setRoadFilter(e.target.value)}>
            <option value="ALL">All roads</option>
            {roads.map(r => (
              <option key={r.id} value={r.id}>{r.id}</option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="sim-error">{error}</div>}

      {events.length > 0 ? (
        <div className="vehicle-table-wrap">
          <h3>Events ({events.length})</h3>
          <table className="steps-table">
            <thead>
              <tr>
                <th>ID</th><th>Type</th><th>Road</th><th>Start</th><th>Duration</th><th>Status</th><th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {events.map(ev => (
                <tr key={ev.id}>
                  <td><code>{ev.id}</code></td>
                  <td>
                    <span className="v-status" style={{ color: EVENT_TYPE_COLOR[ev.type] ?? '#64748b' }}>
                      {ev.type?.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td><code>{ev.affectedRoadId}</code></td>
                  <td>{ev.startTick}</td>
                  <td>{ev.durationTicks}</td>
                  <td>
                    <span className="v-status" style={{ color: EVENT_STATUS_COLOR[ev.status] ?? '#64748b' }}>
                      {ev.status}
                    </span>
                  </td>
                  <td>
                    {ev.status === 'SCHEDULED' && (
                      <button className="sim-btn-xs green" onClick={() => handleActivate(ev.id)}>
                        Activate
                      </button>
                    )}
                    {ev.status !== 'RESOLVED' && (
                      <button className="sim-btn-xs amber" onClick={() => handleResolve(ev.id)}>
                        Resolve
                      </button>
                    )}
                    <button className="sim-btn-xs red" onClick={() => handleDelete(ev.id)}>
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="sim-hint" style={{ marginTop: '0.5rem' }}>
          No events match the current filters. Dispatch a traffic event above — it will activate when the simulation reaches its start tick.
        </p>
      )}
    </section>
  )
}

// ── Statistics & Heatmap Panel (Week 6) ─────────────────────────────────────

const CONGESTION_COLOR = (level) => {
  if (level >= 0.7) return '#dc2626'
  if (level >= 0.4) return '#d97706'
  return '#16a34a'
}

function StatisticsPanel() {
  const [traffic, setTraffic]       = useState(null)
  const [algorithms, setAlgorithms] = useState([])
  const [roads, setRoads]           = useState([])
  const [statsError, setStatsError] = useState(null)
  const [roadsError, setRoadsError] = useState(null)
  const [loading, setLoading]       = useState(false)

  const fetchStats = async () => {
    setStatsError(null)
    try {
      const full = await statisticsApi.getAllStatistics()
      setTraffic(full.traffic ?? null)
      setAlgorithms(Array.isArray(full.algorithms) ? full.algorithms : [])
    } catch (e) {
      setStatsError(e.message)
    }
  }

  const fetchRoads = async () => {
    setRoadsError(null)
    try {
      const rds = await networkApi.getAllRoads()
      setRoads(Array.isArray(rds) ? rds : [])
    } catch (e) {
      setRoadsError(e.message)
    }
  }

  const fetchAll = async () => {
    setLoading(true)
    await Promise.allSettled([fetchStats(), fetchRoads()])
    setLoading(false)
  }

  useEffect(() => { fetchAll() }, [])

  const maxCongestion = roads.length > 0
    ? Math.max(...roads.map(r => r.congestionLevel ?? 0))
    : 0

  return (
    <section className="sim-panel">
      <h2>Statistics &amp; Heatmap</h2>

      <div className="sim-row">
        <button className="sim-btn secondary" onClick={fetchAll} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
        {traffic && (
          <span className="sim-hint">Tick&nbsp;<strong>{traffic.currentTick}</strong></span>
        )}
      </div>

      {/* Traffic Stats Grid */}
      {statsError && <div className="sim-error">Statistics: {statsError}</div>}
      {traffic && (
        <>
          <h3>Traffic Summary</h3>
          <div className="stats-grid">
            <div className="stat-card">
              <span className="stat-label">Total Vehicles</span>
              <span className="stat-value">{traffic.totalVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Arrived</span>
              <span className="stat-value" style={{ color: '#16a34a' }}>{traffic.arrivedVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Moving</span>
              <span className="stat-value" style={{ color: '#2563eb' }}>{traffic.movingVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Blocked</span>
              <span className="stat-value" style={{ color: '#dc2626' }}>{traffic.blockedVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Waiting</span>
              <span className="stat-value" style={{ color: '#64748b' }}>{traffic.waitingVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Avg Congestion</span>
              <span className="stat-value" style={{ color: CONGESTION_COLOR(traffic.averageCongestionLevel) }}>
                {(traffic.averageCongestionLevel * 100).toFixed(1)}%
              </span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Congested Roads</span>
              <span className="stat-value" style={{ color: '#d97706' }}>{traffic.congestedRoadCount}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Blocked Roads</span>
              <span className="stat-value" style={{ color: '#dc2626' }}>{traffic.blockedRoadCount}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Closed Roads</span>
              <span className="stat-value" style={{ color: '#991b1b' }}>{traffic.closedRoadCount}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Active Events</span>
              <span className="stat-value" style={{ color: '#6366f1' }}>{traffic.activeEventCount}</span>
            </div>
          </div>
        </>
      )}

      {/* Road Congestion Heatmap */}
      {roadsError && <div className="sim-error">Heatmap: {roadsError}</div>}
      {roads.length > 0 && (
        <>
          <h3>Road Congestion Heatmap</h3>
          <div className="vehicle-table-wrap">
            <table className="steps-table">
              <thead>
                <tr>
                  <th>Road</th>
                  <th>From → To</th>
                  <th>Status</th>
                  <th>Congestion</th>
                  <th style={{ width: '160px' }}>Heat</th>
                </tr>
              </thead>
              <tbody>
                {roads
                  .slice()
                  .sort((a, b) => (b.congestionLevel ?? 0) - (a.congestionLevel ?? 0))
                  .map(r => {
                    const level = r.congestionLevel ?? 0
                    const pct   = maxCongestion > 0 ? (level / maxCongestion) * 100 : 0
                    const color = CONGESTION_COLOR(level)
                    return (
                      <tr key={r.id}>
                        <td><code>{r.id}</code></td>
                        <td>
                          <code>{r.startIntersectionId}</code>
                          {' → '}
                          <code>{r.endIntersectionId}</code>
                        </td>
                        <td>
                          <span className="v-status" style={{ color: ROAD_STATUS_COLOR[r.status] ?? '#64748b' }}>
                            {r.status}
                          </span>
                        </td>
                        <td style={{ color, fontWeight: 700 }}>
                          {(level * 100).toFixed(1)}%
                        </td>
                        <td>
                          <div style={{ background: '#f1f5f9', borderRadius: 4, height: 10, overflow: 'hidden' }}>
                            <div style={{
                              width: `${pct}%`,
                              height: '100%',
                              background: color,
                              borderRadius: 4,
                              transition: 'width 0.3s',
                            }} />
                          </div>
                        </td>
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {/* Algorithm Info */}
      {algorithms.length > 0 && (
        <>
          <h3>Routing Algorithms</h3>
          <div className="stats-grid">
            {algorithms.map(a => (
              <div className="stat-card" key={a.algorithmName}>
                <span className="stat-label">{a.algorithmName}</span>
                <span className="stat-value algo">{a.supportedPreferences?.join(', ') ?? '—'}</span>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  )
}

// ── Route Finder (Week 3) ─────────────────────────────────────────────────────

export default function App() {
  const [start, setStart]               = useState('')
  const [destination, setDestination]   = useState('')
  const [preference, setPreference]     = useState('SHORTEST_TIME')
  const [result, setResult]             = useState(null)
  const [error, setError]               = useState(null)
  const [loading, setLoading]           = useState(false)

  const findRoute = async () => {
    if (!start.trim() || !destination.trim()) {
      setError('Please enter both start and destination intersection IDs.')
      return
    }
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const data = await routingApi.findRoute(start.trim(), destination.trim(), preference)
      setResult(data)
    } catch (err) {
      const msg = err.message || ''
      setError(
        msg.toLowerCase().includes('fetch') || msg.toLowerCase().includes('failed to fetch')
          ? `Cannot connect to backend. Is the server running on port 7070?\n${msg}`
          : msg
      )
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e) => { if (e.key === 'Enter') findRoute() }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Traffic Route Finder</h1>
        <p className="subtitle">Small City Network — 6 intersections (I1–I6), 17 roads (R01–R17)</p>
      </header>

      <main className="main-content">

        {/* ── Network Info (Week 2) ── */}
        <NetworkPanel />

        {/* ── Simulation (Week 4) ── */}
        <SimulationPanel />

        {/* ── Event Controls (Week 5) ── */}
        <EventsPanel />

        {/* ── Statistics & Heatmap (Week 6) ── */}
        <StatisticsPanel />

        {/* ── Route Finder (Week 3) ── */}
        <section className="form-section">
          <h2>Route Finder</h2>
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="start">Start Intersection</label>
              <input
                id="start"
                type="text"
                value={start}
                onChange={(e) => setStart(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. I1"
              />
            </div>
            <div className="arrow">→</div>
            <div className="form-group">
              <label htmlFor="destination">Destination Intersection</label>
              <input
                id="destination"
                type="text"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. I6"
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="preference">Route Preference</label>
            <select id="preference" value={preference} onChange={(e) => setPreference(e.target.value)}>
              {PREFERENCES.map((p) => (
                <option key={p.value} value={p.value}>{p.label}</option>
              ))}
            </select>
          </div>

          <button className="find-btn" onClick={findRoute} disabled={loading}>
            {loading ? 'Finding Route…' : 'Find Route'}
          </button>
        </section>

        {error && (
          <div className="error-box">
            <strong>Error:</strong> {error}
          </div>
        )}

        {result && (
          <section className="result-section">
            <h2>Route Found</h2>
            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Algorithm</span>
                <span className="stat-value algo">{result.algorithm}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Total Distance</span>
                <span className="stat-value">{result.totalDistance.toFixed(2)} km</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Travel Time</span>
                <span className="stat-value">{(result.estimatedTravelTime * 60).toFixed(1)} min</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Total Cost</span>
                <span className="stat-value">{result.totalCost.toFixed(4)}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Visited Nodes</span>
                <span className="stat-value">{result.visitedNodes}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Computation</span>
                <span className="stat-value">{result.computationTimeMillis} ms</span>
              </div>
            </div>

            <h3>Steps ({result.steps?.length ?? 0})</h3>
            {result.steps && result.steps.length > 0 ? (
              <div className="steps-wrapper">
                <table className="steps-table">
                  <thead>
                    <tr><th>#</th><th>From</th><th>To</th><th>Road ID</th><th>Cost</th></tr>
                  </thead>
                  <tbody>
                    {result.steps.map((step, i) => (
                      <tr key={i}>
                        <td className="step-num">{i + 1}</td>
                        <td><code>{step.from}</code></td>
                        <td><code>{step.to}</code></td>
                        <td><code>{step.roadId}</code></td>
                        <td>{step.cost.toFixed(4)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="no-steps">Start and destination are the same — no steps needed.</p>
            )}
          </section>
        )}

        <section className="hint-section">
          <h3>Available Intersections</h3>
          <div className="intersection-list">
            {[
              { id: 'I1', name: 'Campus Gate' },
              { id: 'I2', name: 'School Junction' },
              { id: 'I3', name: 'Central Market' },
              { id: 'I4', name: 'Riverside Homes' },
              { id: 'I5', name: 'General Hospital' },
              { id: 'I6', name: 'Night District' },
            ].map((i) => (
              <span key={i.id} className="intersection-tag">
                <strong>{i.id}</strong> — {i.name}
              </span>
            ))}
          </div>
        </section>

      </main>
    </div>
  )
}
