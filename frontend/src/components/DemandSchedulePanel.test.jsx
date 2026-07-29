// @vitest-environment jsdom
import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import DemandSchedulePanel from './DemandSchedulePanel'
import {
  formatScheduledTime,
  hourlyDemandCounts,
  shouldShowDemandSchedule,
} from './demandScheduleUtils'

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

  it('hides the schedule in rendering labs and scenarios without demand', () => {
    expect(shouldShowDemandSchedule(12000, true)).toBe(false)
    expect(shouldShowDemandSchedule(0, false)).toBe(false)
    expect(shouldShowDemandSchedule(12000, false)).toBe(true)
  })

  it('renders a read-only 24-hour profile without a misleading hour control', () => {
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
      totalPlans: 1000,
      failedPlans: 0,
      offset: 0,
      limit: 2,
      returnedPlans: 2,
      hourlyPlanCounts: Array.from(
        { length: 24 },
        (_, hour) => (hour === 7 ? 500 : hour === 1 ? 300 : 0),
      ),
      vehicleTypeCounts: { MOTORCYCLE: 895, CAR: 105 },
      purposeCounts: { COMMUTE: 620, VISITOR: 380 },
      plans: [plan('morning', 'COMMUTE', 7), plan('night', 'VISITOR', 1)],
    }

    render(<DemandSchedulePanel schedule={schedule} currentTimeOfDaySeconds={7 * 3600} />)

    expect(screen.getAllByLabelText(/departures$/)).toHaveLength(24)
    expect(screen.getByLabelText('07:00: 500 departures').className)
      .toContain('current')
    expect(screen.queryByLabelText('Hour')).toBeNull()
    expect(screen.queryByRole('button', { name: /departures$/ })).toBeNull()
    expect(screen.getByText(/Vehicles depart automatically/)).toBeTruthy()
    expect(screen.getByText(/Inspecting plans 1–2 of 1000/)).toBeTruthy()
    expect(screen.getByText('Motorcycle 895 · Car 105')).toBeTruthy()
    expect(screen.getByText('Commute 620 · Visitor 380')).toBeTruthy()
    expect(screen.getByText('Home morning · near Home Street · 10.770070, 106.700000')).toBeTruthy()
    expect(screen.getByText('Home night · near Home Street · 10.770010, 106.700000')).toBeTruthy()
  })

  it('requests bounded server pages without changing simulation demand', () => {
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
    const schedule = {
      totalPlans: 25,
      failedPlans: 0,
      offset: 12,
      limit: 12,
      returnedPlans: 12,
      plans: [plan('work', 'WORK', 7), plan('private', 'PRIVATE', 12)],
    }
    const onPageChange = vi.fn()
    const { container } = render(
      <DemandSchedulePanel
        schedule={schedule}
        currentTimeOfDaySeconds={7 * 3600}
        onPageChange={onPageChange}
      />,
    )
    const panel = within(container)

    expect(panel.getByText('2 / 3')).toBeTruthy()
    fireEvent.click(panel.getByRole('button', { name: 'Previous' }))
    expect(onPageChange).toHaveBeenCalledWith(0, 12)
    fireEvent.click(panel.getByRole('button', { name: 'Next' }))
    expect(onPageChange).toHaveBeenCalledWith(24, 12)
    expect(panel.getByText(/all departures still spawn automatically/)).toBeTruthy()
  })
})
