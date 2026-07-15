import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  healthApi,
  networkApi,
  routingApi,
  vehicleApi,
  trafficEventApi,
  simulationApi,
  statisticsApi,
  ApiClient,
} from './trafficAPI'

function mockFetchOnce(body, { ok = true, status = 200 } = {}) {
  global.fetch = vi.fn().mockResolvedValue({
    ok,
    status,
    statusText: ok ? 'OK' : 'Error',
    json: async () => body,
  })
  return global.fetch
}

describe('ApiClient (base HTTP layer)', () => {
  beforeEach(() => {
    global.fetch = vi.fn()
  })

  it('builds GET requests against the configured base URL', async () => {
    const fetchMock = mockFetchOnce({ ok: true })
    await new ApiClient('http://localhost:7070/api').get('/health')
    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:7070/api/health',
      expect.objectContaining({ method: 'GET' })
    )
  })

  it('serializes the body and sets JSON headers for POST requests', async () => {
    const fetchMock = mockFetchOnce({ id: 'v1' })
    await new ApiClient().post('/vehicles', { type: 'CAR' })
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/vehicles'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ type: 'CAR' }),
        headers: expect.objectContaining({ 'Content-Type': 'application/json' }),
      })
    )
  })

  it('returns null for 204 No Content responses', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true, status: 204 })
    const result = await new ApiClient().delete('/events/e1')
    expect(result).toBeNull()
  })

  it('throws with the server-provided message when a request fails', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 404,
      statusText: 'Not Found',
      json: async () => ({ error: 'Vehicle not found' }),
    })
    await expect(new ApiClient().get('/vehicles/missing')).rejects.toThrow('Vehicle not found')
  })

  it('falls back to the HTTP status text when no error body is present', async () => {
    global.fetch = vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      json: async () => { throw new Error('no body') },
    })
    await expect(new ApiClient().get('/broken')).rejects.toThrow('Internal Server Error')
  })
})

describe('healthApi', () => {
  it('checks backend health', async () => {
    const fetchMock = mockFetchOnce({ status: 'UP' })
    await healthApi.checkHealth()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/health'), expect.anything())
  })
})

describe('networkApi (network data for the map)', () => {
  it('fetches the road network summary', async () => {
    const fetchMock = mockFetchOnce({ intersectionCount: 6, roadCount: 17 })
    const data = await networkApi.getRoadNetwork()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/network'), expect.anything())
    expect(data.roadCount).toBe(17)
  })

  it('fetches all intersections (map coordinates)', async () => {
    const fetchMock = mockFetchOnce([{ id: 'I1', x: 0, y: 0 }])
    await networkApi.getAllIntersections()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/network/intersections'),
      expect.anything()
    )
  })

  it('fetches all roads', async () => {
    const fetchMock = mockFetchOnce([{ id: 'R01' }])
    await networkApi.getAllRoads()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/network/roads'), expect.anything())
  })

  it('fetches a single intersection by id', async () => {
    const fetchMock = mockFetchOnce({ id: 'I1' })
    await networkApi.getIntersection('I1')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/network/intersections/I1'),
      expect.anything()
    )
  })
})

describe('routingApi', () => {
  it('requests a route with an uppercased preference', async () => {
    const fetchMock = mockFetchOnce({ algorithm: 'DIJKSTRA', totalDistance: 4.2 })
    await routingApi.findRoute('I1', 'I6', 'shortest_time')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/routes'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          startIntersectionId: 'I1',
          destinationIntersectionId: 'I6',
          preference: 'SHORTEST_TIME',
        }),
      })
    )
  })
})

describe('vehicleApi', () => {
  it('creates a vehicle with type and endpoints', async () => {
    const fetchMock = mockFetchOnce({ id: 'v1' })
    await vehicleApi.createVehicle('CAR', 'I1', 'I6')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/vehicles'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ type: 'CAR', startIntersectionId: 'I1', endIntersectionId: 'I6' }),
      })
    )
  })

  it('lists all vehicles', async () => {
    const fetchMock = mockFetchOnce([{ id: 'v1' }, { id: 'v2' }])
    const vehicles = await vehicleApi.getAllVehicles()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/vehicles'),
      expect.objectContaining({ method: 'GET' })
    )
    expect(vehicles).toHaveLength(2)
  })
})

describe('trafficEventApi', () => {
  it('dispatches a new traffic event', async () => {
    const fetchMock = mockFetchOnce({ id: 'e1', status: 'SCHEDULED' })
    await trafficEventApi.createEvent('ACCIDENT', 'R01', 0, 5, { value: 0.5 })
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/events'),
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          type: 'ACCIDENT',
          affectedRoadId: 'R01',
          startTick: 0,
          durationTicks: 5,
          value: 0.5,
        }),
      })
    )
  })

  it('transitions an event status', async () => {
    const fetchMock = mockFetchOnce({ id: 'e1', status: 'ACTIVE' })
    await trafficEventApi.updateEventStatus('e1', 'ACTIVE')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/events/e1/status'),
      expect.objectContaining({ method: 'PUT', body: JSON.stringify({ status: 'ACTIVE' }) })
    )
  })

  it('removes an event', async () => {
    global.fetch = vi.fn().mockResolvedValue({ ok: true, status: 204 })
    await trafficEventApi.removeEvent('e1')
    expect(global.fetch).toHaveBeenCalledWith(
      expect.stringContaining('/events/e1'),
      expect.objectContaining({ method: 'DELETE' })
    )
  })

  it('fetches events scoped to a specific road', async () => {
    const fetchMock = mockFetchOnce([{ id: 'e1', affectedRoadId: 'R01' }])
    await trafficEventApi.getEventsOnRoad('R01')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/events/road/R01'),
      expect.anything()
    )
  })
})

describe('simulationApi (tick/pause/reset)', () => {
  it('initializes the simulation with vehicles and events', async () => {
    const fetchMock = mockFetchOnce({ status: 'READY' })
    const vehicles = [{ id: 'v1', type: 'CAR' }]
    await simulationApi.initializeSimulation(vehicles, [])
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/simulation/initialize'),
      expect.objectContaining({ body: JSON.stringify({ vehicles, events: [] }) })
    )
  })

  it('starts, pauses, resumes, and resets the simulation', async () => {
    const fetchMock = mockFetchOnce({ status: 'OK' })
    await simulationApi.startSimulation()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/simulation/start'),
      expect.objectContaining({ method: 'POST' })
    )

    await simulationApi.pauseSimulation()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/simulation/pause'),
      expect.objectContaining({ method: 'POST' })
    )

    await simulationApi.resumeSimulation()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/simulation/resume'),
      expect.objectContaining({ method: 'POST' })
    )

    await simulationApi.resetSimulation()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/simulation/reset'),
      expect.objectContaining({ method: 'POST' })
    )
  })

  it('advances exactly one tick per call and returns the snapshot', async () => {
    const fetchMock = mockFetchOnce({ tick: 1, vehicles: [], roads: [], statistics: null })
    const snap = await simulationApi.advanceOneTick()
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/simulation/tick'),
      expect.objectContaining({ method: 'POST' })
    )
    expect(snap.tick).toBe(1)
  })
})

describe('statisticsApi (stats panel)', () => {
  it('fetches the combined traffic + algorithm statistics payload', async () => {
    const fetchMock = mockFetchOnce({
      traffic: { totalVehicles: 3, averageCongestionLevel: 0.2 },
      algorithms: [{ algorithmName: 'DIJKSTRA' }],
    })
    const data = await statisticsApi.getAllStatistics()
    expect(fetchMock).toHaveBeenCalledWith(expect.stringContaining('/statistics'), expect.anything())
    expect(data.traffic.totalVehicles).toBe(3)
    expect(data.algorithms[0].algorithmName).toBe('DIJKSTRA')
  })

  it('fetches statistics for a specific algorithm', async () => {
    const fetchMock = mockFetchOnce({ algorithmName: 'ASTAR' })
    await statisticsApi.getAlgorithmStatistics('ASTAR')
    expect(fetchMock).toHaveBeenCalledWith(
      expect.stringContaining('/statistics/algorithm/ASTAR'),
      expect.anything()
    )
  })
})
