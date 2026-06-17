import { useState, useEffect, useCallback } from 'react'
import { routingApi, statisticsApi } from '../api/trafficApi'
import { Route, AlgorithmStatistics } from '../models'

/**
 * Hook for managing routing algorithms and statistics
 * Integrates with OOP domain models
 */
export function useAlgorithms() {
  const [algorithms, setAlgorithms] = useState([])
  const [selectedAlgorithm, setSelectedAlgorithm] = useState(null)
  const [algorithmStats, setAlgorithmStats] = useState({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  /**
   * Load available algorithms on component mount
   */
  useEffect(() => {
    const loadAlgorithms = async () => {
      try {
        setLoading(true)
        const result = await routingApi.getAvailableAlgorithms()
        const algoList = Array.isArray(result) ? result : []
        setAlgorithms(algoList)
        if (algoList.length > 0 && !selectedAlgorithm) {
          setSelectedAlgorithm(algoList[0])
        }
      } catch (err) {
        setError(`Failed to load algorithms: ${err.message}`)
      } finally {
        setLoading(false)
      }
    }

    loadAlgorithms()
  }, [])

  /**
   * Load statistics for an algorithm with OOP model
   */
  const loadAlgorithmStats = useCallback(async (algorithmName) => {
    try {
      const statsData = await statisticsApi.getAlgorithmStatistics(algorithmName)
      const stats = new AlgorithmStatistics(statsData)
      setAlgorithmStats((prev) => ({
        ...prev,
        [algorithmName]: stats,
      }))
    } catch (err) {
      // Stats might not be available yet
      console.warn(`Failed to load stats for ${algorithmName}:`, err)
    }
  }, [])

  /**
   * Load stats when algorithm is selected
   */
  useEffect(() => {
    if (selectedAlgorithm && !algorithmStats[selectedAlgorithm]) {
      loadAlgorithmStats(selectedAlgorithm)
    }
  }, [selectedAlgorithm, algorithmStats, loadAlgorithmStats])

  /**
   * Find route using selected algorithm
   * Returns Route OOP model
   */
  const findRoute = useCallback(
    async (startId, endId, algorithmOverride = null) => {
      const algo = algorithmOverride || selectedAlgorithm
      if (!algo) throw new Error('No algorithm selected')

      try {
        const result = await routingApi.findRoute(startId, endId, algo)
        // Convert result to Route OOP model if it has route data
        if (result.route) {
          return new Route(result.route)
        }
        return result
      } catch (err) {
        setError(`Route finding failed: ${err.message}`)
        throw err
      }
    },
    [selectedAlgorithm]
  )

  /**
   * Compare algorithms by testing them
   */
  const compareAlgorithms = useCallback(
    async (startId, endId) => {
      const results = {}
      for (const algo of algorithms) {
        try {
          const result = await routingApi.findRoute(startId, endId, algo)
          // Convert to Route model if route data exists
          if (result.route) {
            results[algo] = new Route(result.route)
          } else {
            results[algo] = result
          }
        } catch (err) {
          results[algo] = { error: err.message }
        }
      }
      return results
    },
    [algorithms]
  )

  /**
   * Get efficiency ranking of algorithms
   */
  const getRanking = useCallback(() => {
    const ranked = Object.entries(algorithmStats)
      .map(([name, stats]) => ({
        name,
        stats,
        efficiency: stats.getEfficiencyScore(),
      }))
      .sort((a, b) => b.efficiency - a.efficiency)

    return ranked
  }, [algorithmStats])

  /**
   * Get best performing algorithm by criteria
   */
  const getBestAlgorithm = useCallback(
    (criteria = 'efficiency') => {
      const ranking = getRanking()
      if (ranking.length === 0) return null

      if (criteria === 'efficiency') {
        return ranking[0]
      }

      if (criteria === 'speed') {
        return ranking.reduce((best, current) =>
          current.stats.averageComputationTime < best.stats.averageComputationTime
            ? current
            : best
        )
      }

      if (criteria === 'accuracy') {
        return ranking.reduce((best, current) =>
          current.stats.averagePathLength < best.stats.averagePathLength
            ? current
            : best
        )
      }

      return ranking[0]
    },
    [getRanking]
  )

  return {
    algorithms,
    selectedAlgorithm,
    setSelectedAlgorithm,
    algorithmStats: algorithmStats[selectedAlgorithm] || null,
    loading,
    error,
    findRoute,
    compareAlgorithms,
    loadAlgorithmStats,
    getRanking,
    getBestAlgorithm,
  }
}
