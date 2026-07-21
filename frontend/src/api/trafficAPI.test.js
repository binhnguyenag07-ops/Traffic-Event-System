import { afterEach, describe, expect, it, vi } from 'vitest'
import { experimentApi, scenarioApi } from './trafficAPI'

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
})
