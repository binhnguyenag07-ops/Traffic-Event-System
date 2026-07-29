import { describe, expect, it } from 'vitest'
import { formatTimeOfDay, timeOfDaySeconds } from './simulationTime'

describe('24-hour simulation clock', () => {
  it('derives clock time from scenario start and frame elapsed time', () => {
    expect(formatTimeOfDay(timeOfDaySeconds(
      { elapsedSeconds: 90 },
      6 * 3600,
    ))).toBe('06:01:30')
  })

  it('wraps after midnight', () => {
    expect(formatTimeOfDay(timeOfDaySeconds(
      { elapsedSeconds: 20 },
      23 * 3600 + 59 * 60 + 50,
    ))).toBe('00:00:10')
  })

  it('prefers an authoritative frame clock when the backend supplies one', () => {
    expect(timeOfDaySeconds(
      { elapsedSeconds: 999, timeOfDaySeconds: 8 * 3600 },
      6 * 3600,
    )).toBe(8 * 3600)
  })
})
