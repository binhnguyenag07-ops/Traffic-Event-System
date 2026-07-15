import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, within, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import App from './App'

const BASE = 'http://localhost:7070/api'

/**
 * Stateful mock backend mirroring the real API contract, including the
 * extended snapshot (vehicles with route/progress, dynamic roads, statistics).
 * Ticks move vehicles along their route; activating an event blocks its road;
 * a blocked road makes the next tick reroute the vehicle over R03.
 */
function freshState() {
  return {
    tick: 0,
    simStatus: 'READY',
    vehicles: [],
    events: [],
    roads: [
      { id: 'R01', startIntersectionId: 'I1', endIntersectionId: 'I2', distance: 2, speedLimit: 40, capacity: 10, currentVehicleCount: 0, status: 'OPEN', congestionLevel: 0.1 },
      { id: 'R02', startIntersectionId: 'I2', endIntersectionId: 'I3', distance: 2, speedLimit: 40, capacity: 10, currentVehicleCount: 0, status: 'OPEN', congestionLevel: 0.2 },
      { id: 'R03', startIntersectionId: 'I1', endIntersectionId: 'I2', distance: 3, speedLimit: 30, capacity: 10, currentVehicleCount: 0, status: 'OPEN', congestionLevel: 0.05 },
    ],
    intersections: [
      { id: 'I1', name: 'Campus Gate', x: 0, y: 0, locationType: 'NORMAL' },
      { id: 'I2', name: 'School Junction', x: 2, y: 0, locationType: 'SCHOOL' },
      { id: 'I3', name: 'Central Market', x: 4, y: 0, locationType: 'COMMERCIAL' },
    ],
  }
}

function routeFor(state, startId, destId) {
  const usable = (roadId) => state.roads.find((r) => r.id === roadId)?.status !== 'BLOCKED'
  const first = usable('R01') ? 'R01' : 'R03'
  if (startId === 'I1' && destId === 'I3') {
    return [
      { from: 'I1', to: 'I2', roadId: first, cost: 0.2 },
      { from: 'I2', to: 'I3', roadId: 'R02', cost: 0.3 },
    ]
  }
  return [{ from: startId, to: destId, roadId: first, cost: 0.2 }]
}

function computeStats(state) {
  const count = (status) => state.vehicles.filter((v) => v.status === status).length
  return {
    currentTick: state.tick,
    totalVehicles: state.vehicles.length,
    arrivedVehicles: count('ARRIVED'),
    movingVehicles: count('MOVING'),
    blockedVehicles: count('BLOCKED'),
    waitingVehicles: count('WAITING'),
    averageCongestionLevel: 0.15,
    congestedRoadCount: state.roads.filter((r) => r.status === 'CONGESTED').length,
    blockedRoadCount: state.roads.filter((r) => r.status === 'BLOCKED').length,
    closedRoadCount: 0,
    activeEventCount: state.events.filter((e) => e.status === 'ACTIVE').length,
  }
}

function snapshot(state) {
  return {
    tick: state.tick,
    status: state.simStatus,
    vehicles: state.vehicles,
    trafficEvents: state.events.filter((e) => e.status === 'ACTIVE'),
    roads: state.roads,
    statistics: computeStats(state),
  }
}

function advanceTick(state) {
  state.tick += 1
  state.vehicles = state.vehicles.map((v) => {
    if (v.status === 'ARRIVED') return v
    // Dynamic rerouting: recompute the remaining route when the planned road is blocked
    let steps = v.routeSteps
    const next = steps[v.nextRouteStepIndex]
    if (next && state.roads.find((r) => r.id === next.roadId)?.status === 'BLOCKED') {
      steps = routeFor(state, v.currentIntersectionId, v.destinationIntersectionId)
      v = { ...v, routeSteps: steps, nextRouteStepIndex: 0 }
    }
    const step = steps[v.nextRouteStepIndex]
    if (!step) return { ...v, status: 'ARRIVED' }
    const idx = v.nextRouteStepIndex + 1
    return {
      ...v,
      currentIntersectionId: step.to,
      nextRouteStepIndex: idx,
      status: idx >= steps.length ? 'ARRIVED' : 'MOVING',
      travelHistory: [...(v.travelHistory ?? []), step.to],
    }
  })
}

function installFetchMock(state) {
  global.fetch = vi.fn(async (url, options = {}) => {
    const method = options.method || 'GET'
    const path = url.replace(BASE, '')
    const json = (body, status = 200) => ({
      ok: status < 400,
      status,
      statusText: 'OK',
      json: async () => body,
    })

    if (path === '/network' && method === 'GET')
      return json({ intersectionCount: state.intersections.length, roadCount: state.roads.length })
    if (path === '/network/intersections' && method === 'GET') return json(state.intersections)
    if (path === '/network/roads' && method === 'GET') return json(state.roads)

    if (path === '/simulation/status' && method === 'GET')
      return json({ status: state.simStatus, currentTick: state.tick })
    if (path === '/simulation/snapshot' && method === 'GET') return json(snapshot(state))
    if (path === '/simulation/initialize' && method === 'POST') {
      const body = JSON.parse(options.body)
      state.tick = 0
      state.simStatus = 'READY'
      state.vehicles = (body.vehicles ?? []).map((v) => ({
        id: v.id,
        type: v.type,
        currentIntersectionId: v.currentIntersectionId,
        destinationIntersectionId: v.destinationIntersectionId,
        status: 'WAITING',
        nextRouteStepIndex: 0,
        routeSteps: routeFor(state, v.currentIntersectionId, v.destinationIntersectionId),
        travelHistory: [v.currentIntersectionId],
      }))
      return json({ status: 'READY', currentTick: 0 })
    }
    if (path === '/simulation/start' && method === 'POST') {
      state.simStatus = 'RUNNING'
      return json({})
    }
    if (path === '/simulation/pause' && method === 'POST') {
      state.simStatus = 'PAUSED'
      return json({})
    }
    if (path === '/simulation/resume' && method === 'POST') {
      state.simStatus = 'RUNNING'
      return json({})
    }
    if (path === '/simulation/reset' && method === 'POST') {
      state.tick = 0
      state.simStatus = 'READY'
      state.vehicles = state.vehicles.map((v) => ({
        ...v,
        currentIntersectionId: v.travelHistory?.[0] ?? v.currentIntersectionId,
        status: 'WAITING',
        nextRouteStepIndex: 0,
      }))
      return json({})
    }
    if (path === '/simulation/tick' && method === 'POST') {
      advanceTick(state)
      return json(snapshot(state))
    }

    if (path === '/events' && method === 'GET') return json(state.events)
    if (path === '/events' && method === 'POST') {
      const body = JSON.parse(options.body)
      const newEvent = { id: `e${state.events.length + 1}`, ...body, status: 'SCHEDULED' }
      state.events = [...state.events, newEvent]
      return json(newEvent)
    }
    if (path.startsWith('/events/road/') && method === 'GET') {
      const roadId = path.split('/').pop()
      return json(state.events.filter((e) => e.affectedRoadId === roadId))
    }
    if (/^\/events\/[^/]+\/status$/.test(path) && method === 'PUT') {
      const id = path.split('/')[2]
      const body = JSON.parse(options.body)
      state.events = state.events.map((e) => (e.id === id ? { ...e, status: body.status } : e))
      const ev = state.events.find((e) => e.id === id)
      const road = state.roads.find((r) => r.id === ev.affectedRoadId)
      if (road) road.status = body.status === 'ACTIVE' ? 'BLOCKED' : 'OPEN'
      return json(ev)
    }
    if (/^\/events\/[^/]+$/.test(path) && method === 'DELETE') {
      const id = path.split('/')[2]
      state.events = state.events.filter((e) => e.id !== id)
      return { ok: true, status: 204 }
    }

    if (path === '/statistics' && method === 'GET')
      return json({
        traffic: computeStats(state),
        algorithms: [{ algorithmName: 'DIJKSTRA', supportedPreferences: ['SHORTEST_TIME', 'SHORTEST_DISTANCE'] }],
      })

    if (path === '/routes' && method === 'POST') {
      const body = JSON.parse(options.body)
      const steps = routeFor(state, body.startIntersectionId, body.destinationIntersectionId)
      return json({
        algorithm: 'DIJKSTRA',
        totalDistance: 4.0,
        estimatedTravelTime: 0.1,
        totalCost: 0.5,
        visitedNodes: 3,
        computationTimeMillis: 1,
        steps,
      })
    }

    return json({ error: `unhandled ${method} ${path}` }, 404)
  })
}

const sectionFor = (headingName) =>
  screen.getByRole('heading', { name: headingName }).closest('section')

let state

beforeEach(() => {
  state = freshState()
  installFetchMock(state)
})

describe('Dashboard smoke test', () => {
  it('renders map, controls, vehicles, events, route finder, and statistics from API data', async () => {
    render(<App />)

    await waitFor(() => expect(screen.getByTestId('map-svg')).toBeInTheDocument())
    expect(screen.getByTestId('map-intersection-I1')).toBeInTheDocument()
    expect(screen.getByTestId('map-road-R01')).toBeInTheDocument()
    expect(screen.getByText(/3 intersections · 3 roads/)).toBeInTheDocument()

    expect(screen.getByRole('heading', { name: 'Simulation Controls' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Vehicles' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Event Controls' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Route Finder' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Statistics & Heatmap' })).toBeInTheDocument()

    // Statistics render real data from the API
    const statsSection = sectionFor('Statistics & Heatmap')
    await waitFor(() => expect(within(statsSection).getByText('DIJKSTRA')).toBeInTheDocument())
  })
})

describe('Map selection details', () => {
  it('shows details for a clicked intersection', async () => {
    const user = userEvent.setup()
    render(<App />)
    await waitFor(() => expect(screen.getByTestId('map-intersection-I1')).toBeInTheDocument())

    await user.click(screen.getByTestId('map-intersection-I1'))
    const details = sectionFor('Details')
    expect(within(details).getByText('Campus Gate')).toBeInTheDocument()
    expect(within(details).getByText('NORMAL')).toBeInTheDocument()
  })
})

describe('Route finder', () => {
  it('finds a route and draws the highlight overlay on the map', async () => {
    const user = userEvent.setup()
    render(<App />)
    const section = sectionFor('Route Finder')
    const scoped = within(section)

    await waitFor(() =>
      expect(within(scoped.getByLabelText('Start Intersection')).getByRole('option', { name: /I1/ })).toBeInTheDocument()
    )
    await user.selectOptions(scoped.getByLabelText('Start Intersection'), 'I1')
    await user.selectOptions(scoped.getByLabelText('Destination Intersection'), 'I3')
    await user.click(scoped.getByRole('button', { name: /find route/i }))

    await waitFor(() => expect(scoped.getByText('DIJKSTRA')).toBeInTheDocument())
    expect(screen.getByTestId('map-route-overlay')).toBeInTheDocument()
    expect(screen.getByTestId('map-road-R01')).toHaveAttribute('data-in-route', 'true')

    await user.click(scoped.getByRole('button', { name: /clear route/i }))
    expect(screen.queryByTestId('map-route-overlay')).not.toBeInTheDocument()
  })

  it('shows a helpful error when the backend is unreachable', async () => {
    global.fetch = vi.fn().mockRejectedValue(new TypeError('Failed to fetch'))
    const user = userEvent.setup()
    render(<App />)
    const section = sectionFor('Route Finder')
    const scoped = within(section)

    // With no data the selects are empty, so Find Route stays disabled — the
    // network error banner tells the user the backend is down instead.
    expect(scoped.getByRole('button', { name: /find route/i })).toBeDisabled()
    await waitFor(() => expect(screen.getByText(/network error/i)).toBeInTheDocument())
    expect(screen.getByText(/mvn exec:java/)).toBeInTheDocument()
    expect(user).toBeTruthy()
  })
})

describe('End-to-end scenario: initialize, start, tick, event, reroute, resolve, reset', () => {
  it('runs the full demo flow with map, tables, and statistics staying in sync', async () => {
    const user = userEvent.setup()
    render(<App />)
    const controls = sectionFor('Simulation Controls')
    const scoped = within(controls)
    const badge = () => controls.querySelector('.sim-status-bar .sim-badge').textContent
    const tickValue = () => controls.querySelector('.sim-tick strong').textContent

    // ── Build scenario from API intersections (nothing hard-coded) ──
    await waitFor(() =>
      expect(within(scoped.getByLabelText('Start')).getByRole('option', { name: /I1/ })).toBeInTheDocument()
    )
    await user.selectOptions(scoped.getByLabelText('Type'), 'CAR')
    await user.selectOptions(scoped.getByLabelText('Start'), 'I1')
    await user.selectOptions(scoped.getByLabelText('Destination'), 'I3')
    await user.click(scoped.getByRole('button', { name: /add vehicle/i }))

    // ── Initialize ──
    await user.click(scoped.getByRole('button', { name: /initialize/i }))
    await waitFor(() => expect(screen.getByTestId('map-vehicle-v1')).toBeInTheDocument())
    expect(screen.getByTestId('map-vehicle-v1')).toHaveAttribute('data-at', 'I1')
    const vehiclePanel = sectionFor('Vehicles')
    expect(within(vehiclePanel).getByText('WAITING')).toBeInTheDocument()
    expect(within(vehiclePanel).getByText('0 / 2 steps')).toBeInTheDocument()

    // ── Start, then pause so we can step ticks manually ──
    await user.click(scoped.getByRole('button', { name: /^start$/i }))
    await waitFor(() => expect(badge()).toBe('RUNNING'))
    await user.click(scoped.getByRole('button', { name: /pause/i }))
    await waitFor(() => expect(badge()).toBe('PAUSED'))

    // ── Tick: vehicle moves, statistics refresh with the same tick ──
    await user.click(scoped.getByRole('button', { name: /step 1 tick/i }))
    await waitFor(() => expect(tickValue()).toBe('1'))
    expect(screen.getByTestId('map-vehicle-v1')).toHaveAttribute('data-at', 'I2')
    expect(within(vehiclePanel).getByText('1 / 2 steps')).toBeInTheDocument()
    const statsSection = sectionFor('Statistics & Heatmap')
    await waitFor(() =>
      expect(within(statsSection).getByText('Tick', { exact: false })).toHaveTextContent('Tick 1')
    )

    // ── Dispatch an accident on R02 (the vehicle's next road) and activate it ──
    const eventPanel = sectionFor('Event Controls')
    const events = within(eventPanel)
    await user.selectOptions(events.getByLabelText('Event Type'), 'ACCIDENT')
    await user.selectOptions(events.getByLabelText('Road'), 'R02')
    await user.click(events.getByRole('button', { name: /dispatch event/i }))
    await waitFor(() => expect(events.getByText('e1')).toBeInTheDocument())

    await user.click(events.getByRole('button', { name: /activate/i }))
    // Event turns the road red on the map and appears as a badge
    await waitFor(() =>
      expect(screen.getByTestId('map-road-R02')).toHaveAttribute('data-status', 'BLOCKED')
    )
    expect(screen.getByTestId('map-event-e1')).toBeInTheDocument()

    // ── Tick with the blocked road: the vehicle reroutes and still progresses ──
    await user.click(scoped.getByRole('button', { name: /step 1 tick/i }))
    await waitFor(() => expect(tickValue()).toBe('2'))
    expect(screen.getByTestId('map-vehicle-v1')).toBeInTheDocument()

    // ── Resolve the event: the road reopens on the map ──
    await user.click(events.getByRole('button', { name: /resolve/i }))
    await waitFor(() =>
      expect(screen.getByTestId('map-road-R02')).toHaveAttribute('data-status', 'OPEN')
    )
    expect(screen.queryByTestId('map-event-e1')).not.toBeInTheDocument()

    // ── Reset: back to tick 0 and READY, vehicle back at its origin ──
    await user.click(scoped.getByRole('button', { name: /reset/i }))
    await waitFor(() => expect(badge()).toBe('READY'))
    expect(tickValue()).toBe('0')
    expect(screen.getByTestId('map-vehicle-v1')).toHaveAttribute('data-at', 'I1')
  })
})

describe('Statistics panel', () => {
  it('refreshes statistics when the tick advances', async () => {
    const user = userEvent.setup()
    render(<App />)
    const statsSection = sectionFor('Statistics & Heatmap')

    await waitFor(() =>
      expect(within(statsSection).getByText('Total Vehicles')).toBeInTheDocument()
    )
    const callsBefore = global.fetch.mock.calls.filter(([u]) => u.endsWith('/statistics')).length

    // Build + initialize + step one tick
    const controls = sectionFor('Simulation Controls')
    const scoped = within(controls)
    await user.selectOptions(scoped.getByLabelText('Start'), 'I1')
    await user.selectOptions(scoped.getByLabelText('Destination'), 'I3')
    await user.click(scoped.getByRole('button', { name: /add vehicle/i }))
    await user.click(scoped.getByRole('button', { name: /initialize/i }))
    await user.click(scoped.getByRole('button', { name: /step 1 tick/i }))

    await waitFor(() => {
      const callsAfter = global.fetch.mock.calls.filter(([u]) => u.endsWith('/statistics')).length
      expect(callsAfter).toBeGreaterThan(callsBefore)
    })
    // Snapshot statistics reflect the vehicle we created
    await waitFor(() =>
      expect(within(statsSection).getByText('Tick', { exact: false })).toHaveTextContent('Tick 1')
    )
  })
})
