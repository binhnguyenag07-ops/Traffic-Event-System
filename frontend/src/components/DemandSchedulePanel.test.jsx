// @vitest-environment jsdom
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import DemandSchedulePanel from './DemandSchedulePanel'
import { formatScheduledTime, hourlyDemandCounts } from './demandScheduleUtils'

describe('demand schedule presentation', () => {
  it('groups exact departures into all 24 clock hours', () => {
    const counts = hourlyDemandCounts([
      { departure: { timeOfDaySeconds: 0 } },
      { departure: { timeOfDaySeconds: 3599 } },
      { departure: { timeOfDaySeconds: 3600 } },
      { departure: { timeOfDaySeconds: 23 * 3600 } },
    ])

    expect(counts).toHaveLength(24)
    expect(counts[0]).toBe(2)
    expect(counts[1]).toBe(1)
    expect(counts[23]).toBe(1)
  })

  it('makes next-day returns explicit instead of wrapping ambiguously', () => {
    expect(formatScheduledTime({ dayOffset: 0, clock: '17:30:00' })).toBe('17:30:00')
    expect(formatScheduledTime({ dayOffset: 1, clock: '01:15:00' })).toBe('D+1 01:15:00')
  })

  it('renders all 24 hours and filters the concrete schedule without losing day context', () => {
    const plan = (id, purpose, hour) => ({
      id,
      purpose,
      vehicleType: 'MOTORCYCLE',
      origin: {
        kind: 'BUILDING',
        name: `Home ${id}`,
        accessName: 'Home Street',
        latitude: 10.77 + hour / 100_000,
        longitude: 106.7,
      },
      departure: {
        timeOfDaySeconds: hour * 3600,
        dayOffset: hour < 6 ? 1 : 0,
        clock: `${String(hour).padStart(2, '0')}:00`,
      },
      estimatedFinalArrival: { dayOffset: hour < 6 ? 1 : 0, clock: `${String(hour).padStart(2, '0')}:15` },
      estimatedReturnArrival: null,
      legs: [{
        sequence: 1,
        destination: { kind: 'BUILDING', name: `Office ${id}`, accessName: 'Office Street' },
        estimatedArrival: { dayOffset: hour < 6 ? 1 : 0, clock: `${String(hour).padStart(2, '0')}:15` },
        dwellSeconds: 0,
      }],
    })
    const schedule = {
      totalPlans: 2,
      failedPlans: 0,
      plans: [plan('morning', 'COMMUTE', 7), plan('night', 'VISITOR', 1)],
    }

    render(<DemandSchedulePanel schedule={schedule} currentTimeOfDaySeconds={7 * 3600} />)

    expect(screen.getAllByRole('button', { name: /departures$/ })).toHaveLength(24)
    expect(screen.getByRole('button', { name: '07:00: 1 departures' }).className)
      .toContain('current')
    expect(screen.getByText('Home morning · near Home Street · 10.770070, 106.700000')).toBeTruthy()
    expect(screen.getByText('Home night · near Home Street · 10.770010, 106.700000')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: '01:00: 1 departures' }))

    expect(screen.queryByText('Home morning · near Home Street · 10.770070, 106.700000')).toBeNull()
    expect(screen.getByText('Home night · near Home Street · 10.770010, 106.700000')).toBeTruthy()
  })

  it('resets stale purpose and hour filters when the scenario schedule changes', () => {
    const plan = (id, purpose, hour) => ({
      id,
      purpose,
      vehicleType: 'CAR',
      origin: { kind: 'BUILDING', name: `Origin ${id}` },
      departure: {
        timeOfDaySeconds: hour * 3600,
        dayOffset: 0,
        clock: `${String(hour).padStart(2, '0')}:00`,
      },
      estimatedFinalArrival: { dayOffset: 0, clock: `${String(hour).padStart(2, '0')}:15` },
      estimatedReturnArrival: null,
      legs: [{
        sequence: 1,
        destination: { kind: 'BOUNDARY', name: `Destination ${id}` },
        estimatedArrival: { dayOffset: 0, clock: `${String(hour).padStart(2, '0')}:15` },
        dwellSeconds: 0,
      }],
    })
    const firstSchedule = {
      totalPlans: 2,
      failedPlans: 0,
      plans: [plan('work', 'WORK', 7), plan('private', 'PRIVATE', 12)],
    }
    const { container, rerender } = render(
      <DemandSchedulePanel schedule={firstSchedule} currentTimeOfDaySeconds={7 * 3600} />,
    )
    const panel = within(container)

    fireEvent.change(panel.getByLabelText('Purpose'), { target: { value: 'WORK' } })
    fireEvent.change(panel.getByLabelText('Hour'), { target: { value: '7' } })
    expect(panel.getByLabelText('Purpose').value).toBe('WORK')
    expect(panel.getByLabelText('Hour').value).toBe('7')

    const nextSchedule = {
      totalPlans: 1,
      failedPlans: 0,
      plans: [plan('visitor', 'VISITOR', 18)],
    }
    rerender(
      <DemandSchedulePanel schedule={nextSchedule} currentTimeOfDaySeconds={18 * 3600} />,
    )

    expect(panel.getByLabelText('Purpose').value).toBe('ALL')
    expect(panel.getByLabelText('Hour').value).toBe('ALL')
    expect(panel.getByText('Origin visitor')).toBeTruthy()
  })
})
