import { useState, useEffect } from 'react'
import { VehicleFactory } from '../models'

/**
 * Control Panel Component
 * Manages simulation controls and display
 * Integrates with OOP simulation hook
 */
export default function ControlPanel({
  roadNetworkHook,
  simulationHook,
  onGenerateMap,
  onInitializeSimulation,
  appStatus,
}) {
  const { intersections, roads } = roadNetworkHook
  const {
    isRunning,
    currentTick,
    status,
    error,
    loading,
    start,
    pause,
    resume,
    reset,
    advanceOneTick,
    advanceTicks,
  } = simulationHook

  const [simulationSpeed, setSimulationSpeed] = useState(1)
  const [autoPlay, setAutoPlay] = useState(false)
  const [ticksToAdvance, setTicksToAdvance] = useState(1)
  const [mapGridRows, setMapGridRows] = useState(5)
  const [mapGridCols, setMapGridCols] = useState(5)
  const [vehicleCount, setVehicleCount] = useState(3)
  const [eventCount, setEventCount] = useState(2)

  /**
   * Auto-advance simulation when auto-play is enabled
   */
  useEffect(() => {
    if (!autoPlay || !isRunning) return

    const interval = setInterval(() => {
      advanceOneTick()
    }, 1000 / simulationSpeed)

    return () => clearInterval(interval)
  }, [autoPlay, isRunning, simulationSpeed, advanceOneTick])

  /**
   * Handle map generation
   */
  const handleGenerateMap = async () => {
    try {
      await onGenerateMap(mapGridRows, mapGridCols)
    } catch (err) {
      console.error('Failed to generate map:', err)
    }
  }

  /**
   * Handle simulation initialization with default vehicles and events
   */
  const handleInitializeSimulation = async () => {
    if (!appStatus.networkLoaded) {
      alert('Please generate or load a map first')
      return
    }

    try {
      if (intersections.length < 2) {
        alert('The map needs at least two intersections before simulation can start')
        return
      }

      const vehicleTypes = ['CAR', 'BUS', 'EMERGENCY']
      const eventTypes = ['CONGESTION', 'ACCIDENT', 'ROAD_CLOSURE']

      // Create sample vehicles through the frontend domain factory.
      const vehicles = []
      for (let i = 0; i < vehicleCount; i++) {
        const start = intersections[i % intersections.length]
        const destination = intersections[(i + Math.max(1, Math.floor(intersections.length / 2))) % intersections.length]
        vehicles.push(
          VehicleFactory.createVehicle(
            vehicleTypes[i % vehicleTypes.length],
            start.id,
            destination.id,
            `vehicle-${i}`
          )
        )
      }

      // Create sample events against roads that actually exist in the loaded network.
      const events = []
      for (let i = 0; i < eventCount && roads.length > 0; i++) {
        const road = roads[i % roads.length]
        events.push({
          id: `event-${i}`,
          type: eventTypes[i % eventTypes.length],
          affectedRoadId: road.id,
          startTick: 0,
          durationTicks: 50,
        })
      }

      await onInitializeSimulation(vehicles, events)
    } catch (err) {
      console.error('Failed to initialize simulation:', err)
    }
  }

  /**
   * Handle start simulation
   */
  const handleStart = async () => {
    try {
      await start()
    } catch (err) {
      console.error('Failed to start simulation:', err)
    }
  }

  /**
   * Handle pause simulation
   */
  const handlePause = async () => {
    try {
      await pause()
    } catch (err) {
      console.error('Failed to pause simulation:', err)
    }
  }

  /**
   * Handle resume simulation
   */
  const handleResume = async () => {
    try {
      await resume()
    } catch (err) {
      console.error('Failed to resume simulation:', err)
    }
  }

  /**
   * Handle reset simulation
   */
  const handleReset = async () => {
    try {
      await reset()
    } catch (err) {
      console.error('Failed to reset simulation:', err)
    }
  }

  /**
   * Handle step simulation (single tick)
   */
  const handleStep = async () => {
    try {
      await advanceOneTick()
    } catch (err) {
      console.error('Failed to step simulation:', err)
    }
  }

  /**
   * Handle multi-step simulation (N ticks)
   */
  const handleMultiStep = async () => {
    try {
      await advanceTicks(ticksToAdvance)
    } catch (err) {
      console.error('Failed to advance multiple ticks:', err)
    }
  }

  return (
    <div className="control-panel">
      <h2>Simulation Control</h2>

      {/* Network Setup Section */}
      <div className="control-section">
        <h3>1. Network Setup</h3>
        <div className="network-controls">
          <div className="control-group">
            <label htmlFor="grid-rows">Grid Rows:</label>
            <input
              id="grid-rows"
              type="number"
              min="2"
              max="20"
              value={mapGridRows}
              onChange={(e) => setMapGridRows(Math.max(2, parseInt(e.target.value) || 2))}
              disabled={isRunning}
            />
          </div>
          <div className="control-group">
            <label htmlFor="grid-cols">Grid Cols:</label>
            <input
              id="grid-cols"
              type="number"
              min="2"
              max="20"
              value={mapGridCols}
              onChange={(e) => setMapGridCols(Math.max(2, parseInt(e.target.value) || 2))}
              disabled={isRunning}
            />
          </div>
          <button
            onClick={handleGenerateMap}
            disabled={isRunning || loading}
            className="btn-primary"
          >
            {loading ? 'Generating...' : '🗺️ Generate Map'}
          </button>
        </div>
      </div>

      {/* Simulation Setup Section */}
      <div className="control-section">
        <h3>2. Simulation Setup</h3>
        <div className="simulation-setup">
          <div className="control-group">
            <label htmlFor="vehicle-count">Vehicles:</label>
            <input
              id="vehicle-count"
              type="number"
              min="1"
              max="50"
              value={vehicleCount}
              onChange={(e) => setVehicleCount(Math.max(1, parseInt(e.target.value) || 1))}
              disabled={appStatus.simulationInitialized}
            />
          </div>
          <div className="control-group">
            <label htmlFor="event-count">Events:</label>
            <input
              id="event-count"
              type="number"
              min="0"
              max="20"
              value={eventCount}
              onChange={(e) => setEventCount(Math.max(0, parseInt(e.target.value) || 0))}
              disabled={appStatus.simulationInitialized}
            />
          </div>
          <button
            onClick={handleInitializeSimulation}
            disabled={!appStatus.networkLoaded || appStatus.simulationInitialized || loading}
            className="btn-primary"
          >
            {loading ? 'Initializing...' : '⚙️ Initialize'}
          </button>
        </div>
      </div>

      {/* Status Display */}
      <div className="control-section">
        <h3>3. Simulation Status</h3>
        <div className="status-display">
          <div className="status-item">
            <span className="status-label">Status:</span>
            <span className={`status-value status-${status.toLowerCase()}`}>
              {status}
            </span>
          </div>
          <div className="status-item">
            <span className="status-label">Current Tick:</span>
            <span className="status-value">{currentTick}</span>
          </div>
          <div className="status-item">
            <span className="status-label">Running:</span>
            <span className={`status-value ${isRunning ? 'running' : 'stopped'}`}>
              {isRunning ? '▶ Running' : '⏸ Stopped'}
            </span>
          </div>
          {appStatus.networkStats && (
            <div className="status-item">
              <span className="status-label">Network Congestion:</span>
              <span className="status-value">
                {(appStatus.networkStats.averageCongestion * 100).toFixed(1)}%
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Simulation Controls */}
      <div className="control-section">
        <h3>4. Simulation Controls</h3>
        <div className="control-buttons">
          {status === 'READY' && (
            <button onClick={handleStart} disabled={!appStatus.simulationInitialized || loading} className="btn-primary">
              {loading ? 'Starting...' : '▶ Start'}
            </button>
          )}

          {status === 'RUNNING' && (
            <button onClick={handlePause} disabled={loading} className="btn-primary">
              {loading ? 'Pausing...' : '⏸ Pause'}
            </button>
          )}

          {status === 'PAUSED' && (
            <button onClick={handleResume} disabled={loading} className="btn-primary">
              {loading ? 'Resuming...' : '▶ Resume'}
            </button>
          )}

          <button
            onClick={handleReset}
            disabled={loading || status === 'READY'}
            className="btn-secondary"
          >
            {loading ? 'Resetting...' : '⟲ Reset'}
          </button>
        </div>
      </div>

      {/* Step Controls */}
      <div className="control-section">
        <h3>5. Step Controls</h3>
        <div className="step-controls">
          <button
            onClick={handleStep}
            disabled={loading || isRunning}
            className="btn-secondary"
            title="Advance simulation by 1 tick"
          >
            Step (1)
          </button>
          <div className="multi-step">
            <input
              type="number"
              min="1"
              max="1000"
              value={ticksToAdvance}
              onChange={(e) => setTicksToAdvance(Math.max(1, parseInt(e.target.value) || 1))}
              disabled={loading || isRunning}
              placeholder="Ticks"
            />
            <button
              onClick={handleMultiStep}
              disabled={loading || isRunning}
              className="btn-secondary"
              title="Advance simulation by N ticks"
            >
              Step (N)
            </button>
          </div>
        </div>
      </div>

      {/* Speed Control */}
      <div className="control-section">
        <h3>6. Speed Control</h3>
        <div className="speed-control">
          <label htmlFor="speed-slider">Simulation Speed:</label>
          <div className="speed-slider-container">
            <span>0.5x</span>
            <input
              id="speed-slider"
              type="range"
              min="0.5"
              max="4"
              step="0.5"
              value={simulationSpeed}
              onChange={(e) => setSimulationSpeed(parseFloat(e.target.value))}
            />
            <span>4x</span>
          </div>
          <span className="speed-display">{simulationSpeed.toFixed(1)}x</span>
        </div>
      </div>

      {/* Auto-Play Toggle */}
      <div className="control-section">
        <h3>7. Auto-Play</h3>
        <div className="autoplay-toggle">
          <label htmlFor="autoplay-checkbox">
            <input
              id="autoplay-checkbox"
              type="checkbox"
              checked={autoPlay}
              onChange={(e) => setAutoPlay(e.target.checked)}
              disabled={!isRunning}
            />
            Auto-advance every {(1000 / simulationSpeed).toFixed(0)}ms
          </label>
        </div>
      </div>

      {/* Error Display */}
      {error && (
        <div className="error-message">
          <span className="error-icon">⚠️</span>
          <span>{error}</span>
        </div>
      )}
    </div>
  )
}
