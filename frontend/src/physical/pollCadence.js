export const PHYSICAL_POLL_INTERVAL_MS = 250
export const MAX_IDLE_PHYSICAL_POLL_INTERVAL_MS = 1000

// An unchanged poll is expected between backend frames. Backing off before a
// complete playback interval has elapsed can phase-lock requests just before a
// new frame and hide the transition that contains a turn. Wait slightly longer
// than the server cadence (and at least one second) before treating it as idle.
export const MIN_IDLE_BACKOFF_GRACE_MS = 1000
export const IDLE_BACKOFF_PLAYBACK_MULTIPLIER = 1.25

export function nextPollCadence(result, current, playbackIntervalMs = 1000) {
  if (result !== 'unchanged') {
    return { delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 }
  }

  const unchangedForMs = current.unchangedForMs + current.delayMs
  const finitePlayback = Number.isFinite(playbackIntervalMs)
    ? Math.max(PHYSICAL_POLL_INTERVAL_MS, playbackIntervalMs)
    : 1000
  const graceMs = Math.max(
    MIN_IDLE_BACKOFF_GRACE_MS,
    finitePlayback * IDLE_BACKOFF_PLAYBACK_MULTIPLIER,
  )
  if (unchangedForMs < graceMs) {
    return { delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs }
  }
  return {
    delayMs: Math.min(
      MAX_IDLE_PHYSICAL_POLL_INTERVAL_MS,
      Math.max(PHYSICAL_POLL_INTERVAL_MS, current.delayMs) * 2,
    ),
    unchangedForMs,
  }
}
