import { useState, useCallback } from 'react'
import { routingApi } from '../api/trafficAPI'

export const ROUTE_PREFERENCES = [
  { value: 'SHORTEST_TIME', label: 'Shortest Time' },
  { value: 'SHORTEST_DISTANCE', label: 'Shortest Distance' },
  { value: 'CONGESTION_AWARE', label: 'Congestion Aware' },
  { value: 'MINIMUM_HOPS', label: 'Minimum Hops' },
]

/**
 * Route finding against the real routing API.
 *
 * The backend selects the algorithm from the route *preference* (Strategy
 * pattern), so the UI sends a preference and displays which algorithm the
 * response says handled it. The list of available algorithms comes from the
 * statistics API (see useStatistics), not from a dedicated routing endpoint.
 */
export function useAlgorithms() {
  const [preference, setPreference] = useState('SHORTEST_TIME')
  const [result, setResult] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const findRoute = useCallback(
    async (startId, destinationId) => {
      setLoading(true)
      setError(null)
      try {
        const data = await routingApi.findRoute(startId, destinationId, preference)
        setResult(data)
        return data
      } catch (e) {
        const msg = e.message || ''
        setError(
          msg.toLowerCase().includes('fetch')
            ? `Cannot connect to backend. Is the server running?\n${msg}`
            : msg
        )
        return null
      } finally {
        setLoading(false)
      }
    },
    [preference]
  )

  const clearResult = useCallback(() => {
    setResult(null)
    setError(null)
  }, [])

  return {
    preference,
    setPreference,
    result,
    loading,
    error,
    findRoute,
    clearResult,
  }
}
