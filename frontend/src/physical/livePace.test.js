import { describe, expect, it } from 'vitest'
import {
  demandStartupPace,
  enterPhysicalLivePace,
  PHYSICAL_LIVE_PACE,
} from './livePace'

describe('physical live-camera pace', () => {
  it('replaces a fast overview cadence with one-to-one live time', () => {
    const transition = enterPhysicalLivePace({
      simulationSecondsPerTick: 30,
      playbackIntervalMs: 250,
    })

    expect(transition.shouldApply).toBe(true)
    expect(transition.target).toEqual({
      simulationSecondsPerTick: 0.25,
      playbackIntervalMs: 250,
    })
    expect(
      transition.target.simulationSecondsPerTick * 1000
        / transition.target.playbackIntervalMs,
    ).toBe(1)
    expect(transition.savedBeforeOpen).toEqual({
      simulationSecondsPerTick: 30,
      playbackIntervalMs: 250,
    })
  })

  it('does not issue another parameter write after the live cadence arrives', () => {
    const saved = {
      simulationSecondsPerTick: 30,
      playbackIntervalMs: 250,
    }
    expect(enterPhysicalLivePace(PHYSICAL_LIVE_PACE, saved)).toEqual({
      shouldApply: false,
      savedBeforeOpen: saved,
      target: PHYSICAL_LIVE_PACE,
    })
  })

  it('does not invent an overview pace when the simulation was already live', () => {
    expect(enterPhysicalLivePace(PHYSICAL_LIVE_PACE)).toEqual({
      shouldApply: false,
      savedBeforeOpen: null,
      target: PHYSICAL_LIVE_PACE,
    })
  })

  it('prevents a direct physical link from receiving the overview startup tick', () => {
    const overview = { simulationSecondsPerTick: 30, playbackIntervalMs: 250 }
    expect(demandStartupPace(true, overview)).toBe(PHYSICAL_LIVE_PACE)
    expect(demandStartupPace(false, overview)).toBe(overview)
  })
})
