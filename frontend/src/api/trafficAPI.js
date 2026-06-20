/**
 * Base API Client Class
 * Handles HTTP communication with the backend server
 */
class ApiClient {
  constructor(baseUrl = 'http://localhost:7070/api') {
    this.baseUrl = baseUrl
    this.headers = {
      'Content-Type': 'application/json',
    }
  }

  /**
   * Make an HTTP request to the API
   * @param {string} endpoint - API endpoint path
   * @param {object} options - Fetch options
   * @returns {Promise<any>} - Response data
   */
  async request(endpoint, options = {}) {
    const url = `${this.baseUrl}${endpoint}`
    const mergedOptions = {
      headers: this.headers,
      ...options,
    }

    try {
      const response = await fetch(url, mergedOptions)

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({}))
        throw new Error(errorData.message || `API error: ${response.statusText}`)
      }

      return await response.json()
    } catch (error) {
      console.error(`API call failed: ${endpoint}`, error)
      throw error
    }
  }

  get(endpoint) {
    return this.request(endpoint, { method: 'GET' })
  }

  post(endpoint, body) {
    return this.request(endpoint, {
      method: 'POST',
      body: JSON.stringify(body),
    })
  }

  put(endpoint, body) {
    return this.request(endpoint, {
      method: 'PUT',
      body: JSON.stringify(body),
    })
  }

  delete(endpoint) {
    return this.request(endpoint, { method: 'DELETE' })
  }
}

/**
 * Health Check Service
 */
class HealthService extends ApiClient {
  checkHealth() {
    return this.get('/health')
  }
}

/**
 * Road Network Service
 */
class NetworkService extends ApiClient {
  loadMap(mapFile) {
    return this.post('/network/load', { mapFile })
  }

  getRoadNetwork() {
    return this.get('/network')
  }

  getIntersection(intersectionId) {
    return this.get(`/network/intersections/${intersectionId}`)
  }

  getAllIntersections() {
    return this.get('/network/intersections')
  }

  getRoad(roadId) {
    return this.get(`/network/roads/${roadId}`)
  }

  getAllRoads() {
    return this.get('/network/roads')
  }

  updateRoadCondition(roadId, condition) {
    return this.put(`/network/roads/${roadId}/condition`, { condition })
  }

  generateGridMap(rows, cols) {
    return this.post('/network/generate', { rows, cols })
  }

  saveMap(fileName, format = 'json') {
    return this.post('/network/save', { fileName, format })
  }
}

/**
 * Routing Service
 */
class RoutingService extends ApiClient {
  findRoute(startIntersectionId, destinationIntersectionId, preference = 'SHORTEST_TIME') {
    return this.post('/routes', {
      startIntersectionId,
      destinationIntersectionId,
      preference: preference.toUpperCase(),
    })
  }

  getAvailableAlgorithms() {
    return this.get('/routing/algorithms')
  }

  recalculateRoute(vehicleId, preference = 'SHORTEST_TIME') {
    return this.post(`/routing/recalculate/${vehicleId}`, { preference })
  }
}

/**
 * Vehicle Service
 */
class VehicleService extends ApiClient {
  createVehicle(type, startIntersectionId, endIntersectionId) {
    return this.post('/vehicles', {
      type, // CAR, BUS, EMERGENCY
      startIntersectionId,
      endIntersectionId,
    })
  }

  getAllVehicles() {
    return this.get('/vehicles')
  }

  getVehicle(vehicleId) {
    return this.get(`/vehicles/${vehicleId}`)
  }

  moveVehicle(vehicleId, nextIntersectionId) {
    return this.post(`/vehicles/${vehicleId}/move`, { nextIntersectionId })
  }

  updateVehicleStatus(vehicleId, status) {
    return this.put(`/vehicles/${vehicleId}/status`, { status })
  }

  removeVehicle(vehicleId) {
    return this.delete(`/vehicles/${vehicleId}`)
  }

  getVehiclesAtIntersection(intersectionId) {
    return this.get(`/vehicles/intersection/${intersectionId}`)
  }
}

/**
 * Traffic Event Service
 */
class TrafficEventService extends ApiClient {
  createEvent(type, affectedRoadId, startTick, durationTicks, eventData = {}) {
    return this.post('/events', {
      type, // CONGESTION, ACCIDENT, ROAD_CLOSURE, TRAFFIC_LIGHT_DELAY
      affectedRoadId,
      startTick,
      durationTicks,
      ...eventData,
    })
  }

  getAllEvents() {
    return this.get('/events')
  }

  getEvent(eventId) {
    return this.get(`/events/${eventId}`)
  }

  updateEventStatus(eventId, status) {
    return this.put(`/events/${eventId}/status`, { status })
  }

  removeEvent(eventId) {
    return this.delete(`/events/${eventId}`)
  }

  getActiveEvents(tick) {
    return this.get(`/events/active?tick=${tick}`)
  }

  getEventsOnRoad(roadId) {
    return this.get(`/events/road/${roadId}`)
  }
}

/**
 * Simulation Service
 */
class SimulationService extends ApiClient {
  startSimulation() {
    return this.post('/simulation/start', {})
  }

  pauseSimulation() {
    return this.post('/simulation/pause', {})
  }

  resumeSimulation() {
    return this.post('/simulation/resume', {})
  }

  resetSimulation() {
    return this.post('/simulation/reset', {})
  }

  advanceOneTick() {
    return this.post('/simulation/tick', {})
  }

  getSimulationStatus() {
    return this.get('/simulation/status')
  }

  getSimulationSnapshot() {
    return this.get('/simulation/snapshot')
  }

  initializeSimulation(vehicles, events) {
    return this.post('/simulation/initialize', { vehicles, events })
  }

  getCurrentTick() {
    return this.get('/simulation/tick/current')
  }

  setSimulationParameters(parameters) {
    return this.put('/simulation/parameters', parameters)
  }
}

/**
 * Statistics Service
 */
class StatisticsService extends ApiClient {
  getTrafficStatistics() {
    return this.get('/statistics/traffic')
  }

  getVehicleStatistics(vehicleId) {
    return this.get(`/statistics/vehicle/${vehicleId}`)
  }

  getRoadStatistics(roadId) {
    return this.get(`/statistics/road/${roadId}`)
  }

  getAlgorithmStatistics(algorithmName) {
    return this.get(`/statistics/algorithm/${algorithmName}`)
  }

  getAllStatistics() {
    return this.get('/statistics')
  }

  getHistoricalStatistics(startTick, endTick) {
    return this.get(`/statistics/history?start=${startTick}&end=${endTick}`)
  }

  exportStatisticsReport(format = 'json') {
    return this.get(`/statistics/export?format=${format}`)
  }
}

/**
 * API Facade - Aggregates all services for easy access
 */
class TrafficApiClient {
  constructor(baseUrl = 'http://localhost:7070/api') {
    this.health = new HealthService(baseUrl)
    this.network = new NetworkService(baseUrl)
    this.routing = new RoutingService(baseUrl)
    this.vehicle = new VehicleService(baseUrl)
    this.event = new TrafficEventService(baseUrl)
    this.simulation = new SimulationService(baseUrl)
    this.statistics = new StatisticsService(baseUrl)
  }

  /**
   * Initialize with map and simulation data
   */
  async initializeWithMap(mapFile, vehicles, events) {
    try {
      await this.network.loadMap(mapFile)
      return await this.simulation.initializeSimulation(vehicles, events)
    } catch (error) {
      console.error('Failed to initialize with map:', error)
      throw error
    }
  }

  /**
   * Get complete simulation state
   */
  async getCompleteSimulationState() {
    try {
      const [network, vehicles, events, status, statistics] = await Promise.all([
        this.network.getRoadNetwork(),
        this.vehicle.getAllVehicles(),
        this.event.getAllEvents(),
        this.simulation.getSimulationStatus(),
        this.statistics.getTrafficStatistics(),
      ])

      return {
        network,
        vehicles,
        events,
        status,
        statistics,
      }
    } catch (error) {
      console.error('Failed to get complete simulation state:', error)
      throw error
    }
  }
}

// ==================== Exports ====================

// Create singleton instance of the API client
const apiClient = new TrafficApiClient()

// Export individual services for backward compatibility
export const healthApi = apiClient.health
export const networkApi = apiClient.network
export const routingApi = apiClient.routing
export const vehicleApi = apiClient.vehicle
export const trafficEventApi = apiClient.event
export const simulationApi = apiClient.simulation
export const statisticsApi = apiClient.statistics

// Export the main API client facade
export default apiClient

// Export classes for advanced usage
export { TrafficApiClient, ApiClient, NetworkService, RoutingService, VehicleService, TrafficEventService, SimulationService, StatisticsService, HealthService }

