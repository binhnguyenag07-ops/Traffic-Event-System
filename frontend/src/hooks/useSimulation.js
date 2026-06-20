import { useState, useEffect, useCallback } from 'react'
import { simulationApi } from '../api/trafficApi'
import { SimulationSnapshot } from '../models'

/**
 * Hook for managing simulation state
 * Integrates with OOP domain models
 */
export function useSimulation() {
  const [simulationState, setSimulationState] = useState(null)
  const [isRunning, setIsRunning] = useState(false)
  const [currentTick, setCurrentTick] = useState(0)
  const [status, setStatus] = useState('READY')
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  /**
   * Get current simulation status
   */
  const getSimulationStatus = useCallback(async () => {
    try {
      const statusData = await simulationApi.getSimulationStatus()
      setStatus(statusData.status || 'READY')
      setIsRunning(statusData.status === 'RUNNING')
      setCurrentTick(statusData.currentTick || 0)
      return statusData
    } catch (err) {
      setError(`Failed to get simulation status: ${err.message}`)
      return null
    }
  }, [])

  /**
   * Get current simulation snapshot with OOP models
   */
  const getSnapshot = useCallback(async () => {
    try {
      setLoading(true)
      const snapshotData = await simulationApi.getSimulationSnapshot()
      const snapshot = new SimulationSnapshot(snapshotData)
      setSimulationState(snapshot)
      if (snapshot.tick !== undefined) {
        setCurrentTick(snapshot.tick)
      }
      return snapshot
    } catch (err) {
      setError(`Failed to get snapshot: ${err.message}`)
      return null
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * Initialize simulation
   */
  const initialize = useCallback(async (vehicles, events) => {
    try {
      setLoading(true)
      await simulationApi.initializeSimulation(vehicles, events)
      await getSnapshot()
      await getSimulationStatus()
    } catch (err) {
      setError(`Failed to initialize simulation: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [getSnapshot, getSimulationStatus])

  /**
   * Start simulation
   */
  const start = useCallback(async () => {
    try {
      await simulationApi.startSimulation()
      setIsRunning(true)
      setStatus('RUNNING')
      await getSnapshot()
    } catch (err) {
      setError(`Failed to start simulation: ${err.message}`)
    }
  }, [getSnapshot])

  /**
   * Pause simulation
   */
  const pause = useCallback(async () => {
    try {
      await simulationApi.pauseSimulation()
      setIsRunning(false)
      setStatus('PAUSED')
      await getSnapshot()
    } catch (err) {
      setError(`Failed to pause simulation: ${err.message}`)
    }
  }, [getSnapshot])

  /**
   * Resume simulation
   */
  const resume = useCallback(async () => {
    try {
      await simulationApi.resumeSimulation()
      setIsRunning(true)
      setStatus('RUNNING')
      await getSnapshot()
    } catch (err) {
      setError(`Failed to resume simulation: ${err.message}`)
    }
  }, [getSnapshot])

  /**
   * Reset simulation
   */
  const reset = useCallback(async () => {
    try {
      await simulationApi.resetSimulation()
      setIsRunning(false)
      setStatus('READY')
      setCurrentTick(0)
      await getSnapshot()
    } catch (err) {
      setError(`Failed to reset simulation: ${err.message}`)
    }
  }, [getSnapshot])

  /**
   * Advance by one tick
   */
  const advanceOneTick = useCallback(async () => {
    try {
      await simulationApi.advanceOneTick()
      setCurrentTick((prev) => prev + 1)
      await getSnapshot()
    } catch (err) {
      setError(`Failed to advance tick: ${err.message}`)
    }
  }, [getSnapshot])

  /**
   * Advance by multiple ticks
   */
  const advanceTicks = useCallback(
    async (ticks) => {
      for (let i = 0; i < ticks; i++) {
        await advanceOneTick()
      }
    },
    [advanceOneTick]
  )

  /**
   * Set simulation parameters
   */
  const setParameters = useCallback(async (parameters) => {
    try {
      await simulationApi.setSimulationParameters(parameters)
    } catch (err) {
      setError(`Failed to set parameters: ${err.message}`)
    }
  }, [])

  /**
   * Get active traffic events
   */
  const getActiveEvents = useCallback(() => {
    if (!simulationState) return []
    return simulationState.getActiveEvents()
  }, [simulationState])

  /**
   * Get moving vehicles
   */
  const getMovingVehicles = useCallback(() => {
    if (!simulationState) return []
    return simulationState.getMovingVehicles()
  }, [simulationState])

  /**
   * Get vehicle count by type
   */
  const getVehicleStats = useCallback(() => {
    if (!simulationState) return {}
    return simulationState.getVehicleCountByType()
  }, [simulationState])

  return {
    simulationState,
    setSimulationState,
    isRunning,
    setIsRunning,
    currentTick,
    setCurrentTick,
    status,
    error,
    setError,
    loading,
    getSimulationStatus,
    getSnapshot,
    initialize,
    start,
    pause,
    resume,
    reset,
    advanceOneTick,
    advanceTicks,
    setParameters,
    getActiveEvents,
    getMovingVehicles,
    getVehicleStats,
  }
}
