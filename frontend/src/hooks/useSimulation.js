import { useState, useEffect, useCallback, useRef } from 'react'
import { simulationApi } from '../api/trafficAPI'

/**
 * Owns the simulation lifecycle and the shared per-tick state.
 *
 * Every lifecycle action and every tick refreshes the snapshot, which carries
 * vehicles (with route/progress), active traffic events, dynamic road state,
 * and traffic statistics in a single consistent payload — so the map, tables,
 * and statistics all describe the same tick.
 *
 * Auto-tick stops automatically on pause, reset, error, and unmount.
 */
export function useSimulation() {
  const [status, setStatus] = useState('READY')
  const [tick, setTick] = useState(0)
  const [vehicles, setVehicles] = useState([])
  const [trafficEvents, setTrafficEvents] = useState([])
  const [liveRoads, setLiveRoads] = useState([])
  const [statistics, setStatistics] = useState(null)
  const [initialized, setInitialized] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [autoTick, setAutoTick] = useState(false)
  const [tickInterval, setTickInterval] = useState(1000)
  const intervalRef = useRef(null)

  const applySnapshot = useCallback((snap) => {
    if (!snap) return
    setTick(snap.tick ?? 0)
    if (snap.status) setStatus(snap.status)
    setVehicles(Array.isArray(snap.vehicles) ? snap.vehicles : [])
    setTrafficEvents(Array.isArray(snap.trafficEvents) ? snap.trafficEvents : [])
    setLiveRoads(Array.isArray(snap.roads) ? snap.roads : [])
    setStatistics(snap.statistics ?? null)
  }, [])

  const refreshSnapshot = useCallback(async () => {
    const snap = await simulationApi.getSimulationSnapshot()
    applySnapshot(snap)
    return snap
  }, [applySnapshot])

  const refreshStatus = useCallback(async () => {
    const s = await simulationApi.getSimulationStatus()
    setStatus(s.status ?? 'READY')
    setTick(s.currentTick ?? 0)
  }, [])

  const wrap = useCallback(async (fn) => {
    setLoading(true)
    setError(null)
    try {
      await fn()
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  const initialize = useCallback(
    (vehicleConfigs, eventConfigs = []) =>
      wrap(async () => {
        await simulationApi.initializeSimulation(vehicleConfigs, eventConfigs)
        setAutoTick(false)
        await refreshSnapshot()
        await refreshStatus()
        setInitialized(true)
      }),
    [wrap, refreshSnapshot, refreshStatus]
  )

  const start = useCallback(
    () =>
      wrap(async () => {
        await simulationApi.startSimulation()
        setStatus('RUNNING')
      }),
    [wrap]
  )

  const pause = useCallback(
    () =>
      wrap(async () => {
        await simulationApi.pauseSimulation()
        setAutoTick(false)
        setStatus('PAUSED')
      }),
    [wrap]
  )

  const resume = useCallback(
    () =>
      wrap(async () => {
        await simulationApi.resumeSimulation()
        setStatus('RUNNING')
      }),
    [wrap]
  )

  const reset = useCallback(
    () =>
      wrap(async () => {
        await simulationApi.resetSimulation()
        setAutoTick(false)
        await refreshSnapshot()
        await refreshStatus()
      }),
    [wrap, refreshSnapshot, refreshStatus]
  )

  const stepTick = useCallback(
    () =>
      wrap(async () => {
        const snap = await simulationApi.advanceOneTick()
        applySnapshot(snap)
      }),
    [wrap, applySnapshot]
  )

  // Auto-tick: only while RUNNING; cleared on pause/reset/unmount and on error.
  useEffect(() => {
    if (autoTick && status === 'RUNNING') {
      intervalRef.current = setInterval(() => {
        simulationApi
          .advanceOneTick()
          .then(applySnapshot)
          .catch((e) => {
            setError(e.message)
            setAutoTick(false)
          })
      }, tickInterval)
    }
    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current)
        intervalRef.current = null
      }
    }
  }, [autoTick, status, tickInterval, applySnapshot])

  return {
    status,
    tick,
    vehicles,
    trafficEvents,
    liveRoads,
    statistics,
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
    refreshSnapshot,
    refreshStatus,
  }
}
