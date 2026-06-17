import { useState, useEffect, useCallback } from 'react'
import { networkApi } from '../api/trafficApi'
import { RoadNetwork, Intersection, Road } from '../models'

/**
 * Hook for managing road network data
 * Integrates with OOP domain models
 */
export function useRoadNetwork() {
  const [roadNetwork, setRoadNetwork] = useState(null)
  const [intersections, setIntersections] = useState([])
  const [roads, setRoads] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  /**
   * Load road network and instantiate domain models
   */
  const loadRoadNetwork = useCallback(async () => {
    try {
      setLoading(true)
      setError(null)
      const networkData = await networkApi.getRoadNetwork()
      const network = new RoadNetwork(networkData)
      setRoadNetwork(network)
    } catch (err) {
      setError(`Failed to load road network: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * Load all intersections
   */
  const loadIntersections = useCallback(async () => {
    try {
      setLoading(true)
      const data = await networkApi.getAllIntersections()
      const intList = Array.isArray(data)
        ? data.map((int) => new Intersection(int))
        : []
      setIntersections(intList)
    } catch (err) {
      setError(`Failed to load intersections: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * Load all roads
   */
  const loadRoads = useCallback(async () => {
    try {
      setLoading(true)
      const data = await networkApi.getAllRoads()
      const roadList = Array.isArray(data)
        ? data.map((road) => new Road(road))
        : []
      setRoads(roadList)
    } catch (err) {
      setError(`Failed to load roads: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * Get specific intersection
   */
  const getIntersection = useCallback(async (id) => {
    try {
      const data = await networkApi.getIntersection(id)
      return new Intersection(data)
    } catch (err) {
      setError(`Failed to get intersection: ${err.message}`)
      return null
    }
  }, [])

  /**
   * Get specific road
   */
  const getRoad = useCallback(async (id) => {
    try {
      const data = await networkApi.getRoad(id)
      return new Road(data)
    } catch (err) {
      setError(`Failed to get road: ${err.message}`)
      return null
    }
  }, [])

  /**
   * Update road condition
   */
  const updateRoadCondition = useCallback(async (roadId, condition) => {
    try {
      await networkApi.updateRoadCondition(roadId, condition)
      // Reload roads to get updated data
      await loadRoads()
    } catch (err) {
      setError(`Failed to update road condition: ${err.message}`)
    }
  }, [loadRoads])

  /**
   * Generate a synthetic map
   */
  const generateGridMap = useCallback(async (rows, cols) => {
    try {
      setLoading(true)
      const networkData = await networkApi.generateGridMap(rows, cols)
      const network = new RoadNetwork(networkData)
      setRoadNetwork(network)
      // Reload all data
      await Promise.all([loadIntersections(), loadRoads()])
    } catch (err) {
      setError(`Failed to generate map: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [loadIntersections, loadRoads])

  /**
   * Load map from file
   */
  const loadMapFromFile = useCallback(async (mapFile) => {
    try {
      setLoading(true)
      const networkData = await networkApi.loadMap(mapFile)
      const network = new RoadNetwork(networkData)
      setRoadNetwork(network)
      // Reload all data
      await Promise.all([loadIntersections(), loadRoads()])
    } catch (err) {
      setError(`Failed to load map: ${err.message}`)
    } finally {
      setLoading(false)
    }
  }, [loadIntersections, loadRoads])

  /**
   * Save map to file
   */
  const saveMapToFile = useCallback(async (fileName, format = 'json') => {
    try {
      await networkApi.saveMap(fileName, format)
    } catch (err) {
      setError(`Failed to save map: ${err.message}`)
    }
  }, [])

  /**
   * Get network statistics
   */
  const getNetworkStatistics = useCallback(() => {
    if (!roadNetwork) return null
    return roadNetwork.getStatistics()
  }, [roadNetwork])

  return {
    roadNetwork,
    setRoadNetwork,
    intersections,
    roads,
    loading,
    error,
    setError,
    loadRoadNetwork,
    loadIntersections,
    loadRoads,
    getIntersection,
    getRoad,
    updateRoadCondition,
    generateGridMap,
    loadMapFromFile,
    saveMapToFile,
    getNetworkStatistics,
  }
}
