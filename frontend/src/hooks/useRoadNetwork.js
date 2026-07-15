import { useState, useEffect, useCallback } from 'react'
import { networkApi } from '../api/trafficAPI'

/**
 * Loads the static road network (summary, intersections, roads) from the API.
 *
 * Intersections carry map coordinates (x/y) and never change during a run;
 * road state is dynamic and can be refreshed on demand (e.g. per tick) via
 * refreshRoads(), or replaced entirely by snapshot road state upstream.
 */
export function useRoadNetwork() {
  const [summary, setSummary] = useState(null)
  const [intersections, setIntersections] = useState([])
  const [roads, setRoads] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const refreshRoads = useCallback(async () => {
    try {
      const data = await networkApi.getAllRoads()
      setRoads(Array.isArray(data) ? data : [])
    } catch (err) {
      setError(`Failed to load roads: ${err.message}`)
    }
  }, [])

  const reload = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const [net, ints, rds] = await Promise.all([
        networkApi.getRoadNetwork(),
        networkApi.getAllIntersections(),
        networkApi.getAllRoads(),
      ])
      setSummary(net)
      setIntersections(Array.isArray(ints) ? ints : [])
      setRoads(Array.isArray(rds) ? rds : [])
    } catch (err) {
      setError(`Failed to load network: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  return {
    summary,
    intersections,
    roads,
    loading,
    error,
    reload,
    refreshRoads,
  }
}
