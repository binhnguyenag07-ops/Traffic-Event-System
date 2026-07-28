import { describe, expect, it } from 'vitest'
import {
  nextPollCadence,
  PHYSICAL_POLL_INTERVAL_MS,
} from './pollCadence'

describe('physical-view polling cadence', () => {
  it('does not back off before a normal 1000 ms playback frame is due', () => {
    let cadence = { delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 }
    for (let index = 0; index < 4; index += 1) {
      cadence = nextPollCadence('unchanged', cadence)
      expect(cadence.delayMs).toBe(PHYSICAL_POLL_INTERVAL_MS)
    }
  })

  it('also respects the slower 2000 ms playback interval', () => {
    let cadence = { delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 }
    for (let index = 0; index < 8; index += 1) {
      cadence = nextPollCadence('unchanged', cadence, 2000)
      expect(cadence.delayMs).toBe(PHYSICAL_POLL_INTERVAL_MS)
    }
  })

  it('backs off after sustained idle time and remains bounded', () => {
    let cadence = { delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 }
    for (let index = 0; index < 5; index += 1) {
      cadence = nextPollCadence('unchanged', cadence)
    }
    expect(cadence.delayMs).toBe(500)

    cadence = nextPollCadence('unchanged', cadence)
    expect(cadence.delayMs).toBe(1000)
    expect(nextPollCadence('unchanged', cadence).delayMs).toBe(1000)
  })

  it('resets immediately when a new frame arrives or a request is retried', () => {
    const idle = { delayMs: 1000, unchangedForMs: 9000 }
    expect(nextPollCadence('updated', idle))
      .toEqual({ delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 })
    expect(nextPollCadence('error', idle))
      .toEqual({ delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 })
  })
})
