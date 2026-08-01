import { afterEach, describe, expect, it, vi } from 'vitest'
import { experimentApi, request, scenarioApi, simulationApi } from './trafficAPI'

afterEach(() => vi.restoreAllMocks())

describe('extended traffic API', () => {
  it('uploads maps as multipart data without forcing a JSON content type', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      filename: 'district.json', intersectionCount: 2, roadCount: 2,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
    const file = new Blob(['{}'], { type: 'application/json' })

    const result = await scenarioApi.importMap(file)

    expect(result.filename).toBe('district.json')
    const [, options] = fetchMock.mock.calls[0]
    expect(options.method).toBe('POST')
    expect(options.body).toBeInstanceOf(FormData)
    expect(options.headers).toBeUndefined()
  })

  it('loads the latest experiment baseline', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({ seed: 42 }), {
      status: 200, headers: { 'Content-Type': 'application/json' },
    }))

    await expect(experimentApi.latest()).resolves.toEqual({ seed: 42 })
    expect(fetchMock).toHaveBeenCalledWith('/api/experiments/latest', expect.any(Object))
  })

  it('loads an authoritative frame at a recorded simulation second', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      timelineId: 'timeline-1', frameId: 12, elapsedSeconds: 3.5,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await expect(simulationApi.frameAt(3.5)).resolves.toMatchObject({ frameId: 12 })
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/simulation/trace/frames/at?elapsedSeconds=3.5',
      expect.any(Object),
    )
  })

  it('loads only one bounded page of the demand schedule', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      totalPlans: 39000,
      offset: 24,
      limit: 12,
      returnedPlans: 12,
      plans: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await simulationApi.demandSchedule(24, 12)

    expect(fetchMock).toHaveBeenCalledWith(
      '/api/simulation/demand/schedule?offset=24&limit=12',
      expect.any(Object),
    )
  })

  it('loads full physical geometry once and polls its light frame state separately', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      origin: { latitude: 10.77, longitude: 106.69 },
      radiusMetres: 1500,
      vehicles: [],
      signals: [],
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await simulationApi.physicalView(
      10.77,
      106.69,
      220,
      { fullMap: true, includeState: false },
    )
    await simulationApi.physicalViewState(10.77, 106.69, 1500)

    expect(fetchMock.mock.calls[0][0]).toBe(
      '/api/simulation/physical-view?lat=10.77&lon=106.69&radiusMetres=220&fullMap=true'
        + '&includeState=false',
    )
    expect(fetchMock.mock.calls[1][0]).toBe(
      '/api/simulation/physical-view/state?lat=10.77&lon=106.69&radiusMetres=1500',
    )
  })

  it('changes playback cadence without changing the simulation step size', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 'RUNNING',
      simulationSecondsPerTick: 3,
      playbackIntervalMs: 250,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await simulationApi.setPlaybackInterval(250)

    const [, options] = fetchMock.mock.calls[0]
    expect(options.method).toBe('PUT')
    expect(options.headers).toMatchObject({ 'X-Traffic-Pace-Protocol': '2' })
    expect(JSON.parse(options.body)).toEqual({ playbackIntervalMs: 250 })
  })

  it('claims current playback control when loading simulation status', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 'READY',
      simulationSecondsPerTick: 0.5,
      playbackIntervalMs: 1000,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await simulationApi.status()

    const [, options] = fetchMock.mock.calls[0]
    expect(options.headers).toMatchObject({
      'Content-Type': 'application/json',
      'X-Traffic-Pace-Protocol': '2',
    })
  })

  it('conditionally restores a camera pace without overwriting a newer choice', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      status: 'RUNNING',
      simulationSecondsPerTick: 2.5,
      playbackIntervalMs: 250,
      parametersApplied: false,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await simulationApi.setParametersIfCurrent(30, 250, 0.25, 250)

    const [, options] = fetchMock.mock.calls[0]
    expect(options.method).toBe('PUT')
    expect(options.headers).toMatchObject({ 'X-Traffic-Pace-Protocol': '2' })
    expect(JSON.parse(options.body)).toEqual({
      simulationSecondsPerTick: 30,
      playbackIntervalMs: 250,
      expectedSimulationSecondsPerTick: 0.25,
      expectedPlaybackIntervalMs: 250,
    })
  })

  it('preserves the HTTP status on API errors', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(JSON.stringify({
      error: 'Vehicle not found: EMS-01',
    }), { status: 404, headers: { 'Content-Type': 'application/json' } }))

    await expect(request('/vehicles/EMS-01', { method: 'DELETE' })).rejects.toMatchObject({
      message: 'Vehicle not found: EMS-01',
      status: 404,
    })
  })
})
