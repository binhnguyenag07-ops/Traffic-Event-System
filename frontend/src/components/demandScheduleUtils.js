export function formatScheduledTime(time) {
  if (!time) return '—'
  return `${time.dayOffset > 0 ? `D+${time.dayOffset} ` : ''}${time.clock}`
}

export function hourlyDemandCounts(plans) {
  const counts = Array.from({ length: 24 }, () => 0)
  ;(plans || []).forEach((plan) => {
    const seconds = Number(plan?.departure?.timeOfDaySeconds)
    if (!Number.isFinite(seconds)) return
    counts[Math.floor((((seconds % 86400) + 86400) % 86400) / 3600)] += 1
  })
  return counts
}
