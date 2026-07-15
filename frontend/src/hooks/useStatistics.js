import { useState, useEffect, useCallback } from 'react'
import { statisticsApi } from '../api/trafficAPI'

/**
 * Loads traffic + algorithm statistics from the statistics API.
 *
 * Pass the current simulation tick to keep statistics in sync with the
 * simulation: every tick change triggers a refresh, so no free-running
 * polling interval is needed (and nothing to leak on unmount).
 */
export function useStatistics(tick = 0) {
  const [traffic, setTraffic] = useState(null)
  const [algorithms, setAlgorithms] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const refresh = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const full = await statisticsApi.getAllStatistics()
      setTraffic(full.traffic ?? null)
      setAlgorithms(Array.isArray(full.algorithms) ? full.algorithms : [])
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh, tick])

  return { traffic, algorithms, loading, error, refresh }
}
