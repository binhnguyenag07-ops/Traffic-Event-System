const SECONDS_PER_DAY = 24 * 60 * 60

export function timeOfDaySeconds(frame, startTimeOfDaySeconds = 0) {
  const authoritative = Number(frame?.timeOfDaySeconds)
  if (Number.isFinite(authoritative)) {
    return ((authoritative % SECONDS_PER_DAY) + SECONDS_PER_DAY) % SECONDS_PER_DAY
  }
  const elapsed = Number(frame?.elapsedSeconds) || 0
  const start = Number(startTimeOfDaySeconds) || 0
  return (((start + elapsed) % SECONDS_PER_DAY) + SECONDS_PER_DAY) % SECONDS_PER_DAY
}

export function formatTimeOfDay(seconds) {
  const whole = Math.floor((((Number(seconds) || 0) % SECONDS_PER_DAY) + SECONDS_PER_DAY)
    % SECONDS_PER_DAY)
  const hours = Math.floor(whole / 3600)
  const minutes = Math.floor((whole % 3600) / 60)
  const remainder = whole % 60
  return [hours, minutes, remainder].map((part) => String(part).padStart(2, '0')).join(':')
}
