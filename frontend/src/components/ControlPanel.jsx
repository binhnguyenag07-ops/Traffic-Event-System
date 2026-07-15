import { useState } from 'react'
import { SIM_STATUS_COLOR } from '../theme'

const VEHICLE_TYPES = ['CAR', 'BUS', 'EMERGENCY']

/**
 * Simulation lifecycle controls plus a scenario builder.
 *
 * Vehicles are composed from the live intersection list (no hard-coded ids):
 * pick type/start/destination one by one, or seed a quick random scenario.
 */
export default function ControlPanel({ sim, intersections = [] }) {
  const {
    status,
    tick,
    initialized,
    loading,
    error,
    autoTick,
    setAutoTick,
    tickInterval,
    setTickInterval,
    initialize,
    start,
    pause,
    resume,
    reset,
    stepTick,
  } = sim

  const [pendingVehicles, setPendingVehicles] = useState([])
  const [type, setType] = useState('CAR')
  const [startId, setStartId] = useState('')
  const [destId, setDestId] = useState('')
  const [formError, setFormError] = useState(null)

  const addVehicle = () => {
    if (!startId || !destId) {
      setFormError('Pick a start and a destination intersection.')
      return
    }
    if (startId === destId) {
      setFormError('Start and destination must differ.')
      return
    }
    setFormError(null)
    setPendingVehicles((list) => [
      ...list,
      {
        id: `v${list.length + 1}`,
        type,
        currentIntersectionId: startId,
        destinationIntersectionId: destId,
      },
    ])
    setStartId('')
    setDestId('')
  }

  const addRandomVehicles = (count = 3) => {
    if (intersections.length < 2) return
    setFormError(null)
    setPendingVehicles((list) => {
      const next = [...list]
      for (let n = 0; n < count; n++) {
        const shuffled = [...intersections].sort(() => Math.random() - 0.5)
        next.push({
          id: `v${next.length + 1}`,
          type: VEHICLE_TYPES[Math.floor(Math.random() * VEHICLE_TYPES.length)],
          currentIntersectionId: shuffled[0].id,
          destinationIntersectionId: shuffled[1].id,
        })
      }
      return next
    })
  }

  const removeVehicle = (id) =>
    setPendingVehicles((list) => list.filter((v) => v.id !== id))

  const handleInitialize = () => {
    if (pendingVehicles.length === 0) {
      setFormError('Add at least one vehicle to the scenario first.')
      return
    }
    setFormError(null)
    initialize(pendingVehicles, [])
  }

  const isRunning = status === 'RUNNING'
  const isPaused = status === 'PAUSED'
  const isReady = status === 'READY'

  return (
    <section className="sim-panel">
      <h2>Simulation Controls</h2>

      <div className="sim-status-bar">
        <span className="sim-badge" style={{ background: SIM_STATUS_COLOR[status] ?? '#64748b' }}>
          {status}
        </span>
        <span className="sim-tick">
          Tick&nbsp;<strong>{tick}</strong>
        </span>
      </div>

      {/* Scenario builder */}
      <h3>Scenario</h3>
      <div className="form-row" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div className="form-group" style={{ flex: '0 0 110px' }}>
          <label htmlFor="cp-type">Type</label>
          <select id="cp-type" value={type} onChange={(e) => setType(e.target.value)}>
            {VEHICLE_TYPES.map((t) => (
              <option key={t} value={t}>{t}</option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label htmlFor="cp-start">Start</label>
          <select id="cp-start" value={startId} onChange={(e) => setStartId(e.target.value)}>
            <option value="">Select…</option>
            {intersections.map((i) => (
              <option key={i.id} value={i.id}>
                {i.id} — {i.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label htmlFor="cp-dest">Destination</label>
          <select id="cp-dest" value={destId} onChange={(e) => setDestId(e.target.value)}>
            <option value="">Select…</option>
            {intersections.map((i) => (
              <option key={i.id} value={i.id}>
                {i.id} — {i.name}
              </option>
            ))}
          </select>
        </div>
      </div>

      <div className="sim-row">
        <button className="sim-btn secondary" onClick={addVehicle}>
          + Add Vehicle
        </button>
        <button className="sim-btn secondary" onClick={() => addRandomVehicles(3)}>
          + 3 Random
        </button>
        {pendingVehicles.length > 0 && (
          <button className="sim-btn secondary" onClick={() => setPendingVehicles([])}>
            Clear
          </button>
        )}
      </div>

      {pendingVehicles.length > 0 && (
        <ul className="pending-vehicles">
          {pendingVehicles.map((v) => (
            <li key={v.id}>
              <code>{v.id}</code> {v.type} {v.currentIntersectionId} → {v.destinationIntersectionId}
              <button
                className="sim-btn-xs red"
                onClick={() => removeVehicle(v.id)}
                aria-label={`Remove ${v.id}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}

      {formError && <div className="sim-error">{formError}</div>}

      <div className="sim-row">
        <button className="sim-btn primary" onClick={handleInitialize} disabled={loading}>
          Initialize
        </button>
        <span className="sim-hint">
          {pendingVehicles.length > 0
            ? `${pendingVehicles.length} vehicle(s) in scenario`
            : 'Build a scenario, then initialize'}
        </span>
      </div>

      {/* Lifecycle */}
      <h3>Run</h3>
      <div className="sim-row">
        {isReady && (
          <button className="sim-btn green" onClick={start} disabled={!initialized || loading}>
            Start
          </button>
        )}
        {isRunning && (
          <button className="sim-btn amber" onClick={pause} disabled={loading}>
            Pause
          </button>
        )}
        {isPaused && (
          <button className="sim-btn green" onClick={resume} disabled={loading}>
            Resume
          </button>
        )}
        <button
          className="sim-btn red"
          onClick={reset}
          disabled={!initialized || isReady || loading}
        >
          Reset
        </button>
        <button
          className="sim-btn secondary"
          onClick={stepTick}
          disabled={loading || isRunning}
          title="Advance one tick manually"
        >
          Step 1 Tick
        </button>
      </div>

      <div className="sim-row">
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
          aria-label="Auto-tick interval"
        >
          <option value={2000}>2 s</option>
          <option value={1000}>1 s</option>
          <option value={500}>0.5 s</option>
          <option value={250}>0.25 s</option>
        </select>
      </div>

      {error && <div className="sim-error">{error}</div>}
    </section>
  )
}
