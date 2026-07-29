export const PHYSICAL_STATUS_POLL_INTERVAL_MS = 1000

export function physicalCameraPresentation(
  status,
  simulationSecondsPerTick,
  playbackIntervalMs,
  connected = true,
) {
  if (!connected) {
    return {
      cameraLabel: 'Simulation status unavailable',
      paceLabel: 'reconnecting status',
      tone: 'disconnected',
    }
  }

  const normalized = String(status || 'READY').toUpperCase()
  if (normalized !== 'RUNNING') {
    return {
      cameraLabel: normalized === 'PAUSED'
        ? 'Paused at current frame'
        : `${normalized.toLowerCase()} frame`,
      paceLabel: 'clock not advancing',
      tone: normalized.toLowerCase(),
    }
  }

  const interval = Number(playbackIntervalMs)
  const step = Number(simulationSecondsPerTick)
  const rate = interval > 0 && Number.isFinite(step) ? step * 1000 / interval : NaN
  return {
    cameraLabel: 'Live camera',
    paceLabel: Number.isFinite(rate) ? `${rate.toFixed(rate < 10 ? 1 : 0)}× simulation time` : 'running',
    tone: 'running',
  }
}
