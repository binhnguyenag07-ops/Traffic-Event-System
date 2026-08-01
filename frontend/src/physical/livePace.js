/**
 * Physical View is a live camera, not a day-overview transport control.
 *
 * Four authoritative frames per wall-clock second are frequent enough to
 * retain lane changes and connector turns, while a quarter simulation second
 * per frame keeps the camera at real time (1 simulated second / wall second).
 */
export const PHYSICAL_LIVE_PACE = Object.freeze({
  simulationSecondsPerTick: 0.25,
  playbackIntervalMs: 250,
})

export function sameSimulationPace(left, right) {
  return Math.abs(
    Number(left?.simulationSecondsPerTick) - Number(right?.simulationSecondsPerTick),
  ) < 1e-9
    && Number(left?.playbackIntervalMs) === Number(right?.playbackIntervalMs)
}

/**
 * Describes the one reversible pace transition made when the live camera opens.
 *
 * This deliberately contains no elapsed-time or time-of-day field: entering a
 * camera may change frame cadence, but can never seek, tick, reset, or otherwise
 * move the simulation clock.
 */
export function enterPhysicalLivePace(current, savedBeforeOpen = null) {
  if (sameSimulationPace(current, PHYSICAL_LIVE_PACE)) {
    return {
      shouldApply: false,
      savedBeforeOpen,
      target: PHYSICAL_LIVE_PACE,
    }
  }
  return {
    shouldApply: true,
    savedBeforeOpen: savedBeforeOpen ?? {
      simulationSecondsPerTick: Number(current?.simulationSecondsPerTick),
      playbackIntervalMs: Number(current?.playbackIntervalMs),
    },
    target: PHYSICAL_LIVE_PACE,
  }
}

/**
 * Restores the overview cadence only while the live-camera cadence still owns
 * the server value. Another dashboard may have selected a newer pace while
 * this camera was open; in that case the newer explicit choice must win.
 */
export function leavePhysicalLivePace(current, savedBeforeOpen = null) {
  if (!savedBeforeOpen || !sameSimulationPace(current, PHYSICAL_LIVE_PACE)) {
    return {
      shouldApply: false,
      target: savedBeforeOpen,
    }
  }
  return {
    shouldApply: !sameSimulationPace(savedBeforeOpen, PHYSICAL_LIVE_PACE),
    target: savedBeforeOpen,
  }
}

export function demandStartupPace(physicalLiveMode, overviewPace) {
  return physicalLiveMode ? PHYSICAL_LIVE_PACE : overviewPace
}
