import { useState, useEffect, useRef } from 'react'

const API_BASE = 'http://localhost:7070/api'

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

const STATUS_COLOR = { READY: '#6366f1', RUNNING: '#16a34a', PAUSED: '#d97706' }
const VEHICLE_STATUS_COLOR = { MOVING: '#2563eb', ARRIVED: '#16a34a', BLOCKED: '#dc2626', WAITING: '#64748b' }

async function apiFetch(path, method = 'GET', body) {
  const res = await fetch(`${API_BASE}${path}`, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  })
  const data = await res.json()
  if (!res.ok) throw new Error(data.error || `${res.status} ${res.statusText}`)
  return data
}

// ── Simulation Panel ────────────────────────────────────────────────────────

function SimulationPanel() {
  const [simStatus, setSimStatus]     = useState('READY')
  const [currentTick, setCurrentTick] = useState(0)
  const [vehicles, setVehicles]       = useState([])
  const [initialized, setInitialized] = useState(false)
  const [loading, setLoading]         = useState(false)
  const [error, setSimError]          = useState(null)
  const [autoTick, setAutoTick]       = useState(false)
  const [tickInterval, setTickInterval] = useState(1000)
  const autoTickRef = useRef(null)

  const wrap = async (fn) => {
    setLoading(true)
    setSimError(null)
    try { await fn() } catch (e) { setSimError(e.message) } finally { setLoading(false) }
  }

  const refreshSnapshot = async () => {
    const snap = await apiFetch('/simulation/snapshot')
    setCurrentTick(snap.tick ?? 0)
    setVehicles(snap.vehicles ?? [])
  }

  const refreshStatus = async () => {
    const s = await apiFetch('/simulation/status')
    setSimStatus(s.status)
    setCurrentTick(s.currentTick ?? 0)
  }

  const handleInitialize = () => wrap(async () => {
    await apiFetch('/simulation/initialize', 'POST', { vehicles: DEFAULT_VEHICLES, events: [] })
    await refreshSnapshot()
    await refreshStatus()
    setInitialized(true)
    setAutoTick(false)
  })

  const handleStart = () => wrap(async () => {
    await apiFetch('/simulation/start', 'POST', {})
    setSimStatus('RUNNING')
  })

  const handlePause = () => wrap(async () => {
    await apiFetch('/simulation/pause', 'POST', {})
    setSimStatus('PAUSED')
    setAutoTick(false)
  })

  const handleResume = () => wrap(async () => {
    await apiFetch('/simulation/resume', 'POST', {})
    setSimStatus('RUNNING')
  })

  const handleReset = () => wrap(async () => {
    await apiFetch('/simulation/reset', 'POST', {})
    setAutoTick(false)
    await refreshSnapshot()
    await refreshStatus()
  })

  const handleTick = () => wrap(async () => {
    const snap = await apiFetch('/simulation/tick', 'POST', {})
    setCurrentTick(snap.tick ?? currentTick + 1)
    setVehicles(snap.vehicles ?? [])
  })

  // Auto-tick effect
  useEffect(() => {
    if (autoTick && simStatus === 'RUNNING') {
      autoTickRef.current = setInterval(() => {
        apiFetch('/simulation/tick', 'POST', {})
          .then((snap) => {
            setCurrentTick(snap.tick ?? 0)
            setVehicles(snap.vehicles ?? [])
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
                <th>ID</th>
                <th>Type</th>
                <th>Current</th>
                <th>Destination</th>
                <th>Status</th>
                <th>Steps</th>
              </tr>
            </thead>
            <tbody>
              {vehicles.map((v) => (
                <tr key={v.id}>
                  <td><code>{v.id}</code></td>
                  <td>{v.type}</td>
                  <td><code>{v.currentIntersectionId}</code></td>
                  <td><code>{v.destinationIntersectionId}</code></td>
                  <td>
                    <span
                      className="v-status"
                      style={{ color: VEHICLE_STATUS_COLOR[v.status] ?? '#64748b' }}
                    >
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
    </section>
  )
}

// ── Route Finder ─────────────────────────────────────────────────────────────

export default function App() {
  const [start, setStart]           = useState('')
  const [destination, setDestination] = useState('')
  const [preference, setPreference] = useState('SHORTEST_TIME')
  const [result, setResult]         = useState(null)
  const [error, setError]           = useState(null)
  const [loading, setLoading]       = useState(false)

  const findRoute = async () => {
    if (!start.trim() || !destination.trim()) {
      setError('Please enter both start and destination intersection IDs.')
      return
    }
    setLoading(true)
    setError(null)
    setResult(null)
    try {
      const response = await fetch(`${API_BASE}/routes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          startIntersectionId: start.trim(),
          destinationIntersectionId: destination.trim(),
          preference,
        }),
      })
      const data = await response.json()
      if (!response.ok) {
        setError(data.error || `Error ${response.status}: ${response.statusText}`)
        return
      }
      setResult(data)
    } catch (err) {
      setError(`Cannot connect to backend. Is the server running on port 7070?\n${err.message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e) => { if (e.key === 'Enter') findRoute() }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Traffic Route Finder</h1>
        <p className="subtitle">Small City Network — 6 intersections (I1–I6)</p>
      </header>

      <main className="main-content">

        {/* ── Simulation ── */}
        <SimulationPanel />

        {/* ── Route Finder ── */}
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
