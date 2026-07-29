import { memo, useMemo } from 'react'
import { formatScheduledTime, hourlyDemandCounts } from './demandScheduleUtils'

function humanEnum(value) {
  return String(value || '')
    .toLowerCase()
    .replaceAll('_', ' ')
    .replace(/\b\p{L}/gu, (letter) => letter.toUpperCase())
}

function placeName(place) {
  if (!place) return 'Unknown place'
  const latitude = Number(place.latitude)
  const longitude = Number(place.longitude)
  const coordinate = Number.isFinite(latitude) && Number.isFinite(longitude)
    ? `${latitude.toFixed(6)}, ${longitude.toFixed(6)}`
    : ''
  let label
  if (place.kind === 'BUILDING' && place.accessName && place.name !== place.accessName) {
    label = `${place.name} · near ${place.accessName}`
  } else {
    label = place.name || place.accessName || 'Unknown place'
  }
  return coordinate ? `${label} · ${coordinate}` : label
}

function PlanCard({ plan }) {
  const firstDestination = plan.legs?.[0]?.destination
  const finalDestination = plan.legs?.at(-1)?.destination
  return (
    <details className="demand-plan">
      <summary>
        <span><strong>{formatScheduledTime(plan.departure)}</strong> {humanEnum(plan.purpose)}</span>
        <b>{plan.vehicleType}</b>
      </summary>
      <div className="demand-plan-body">
        <p><strong>{placeName(plan.origin)}</strong><span>→</span>
          <strong>{placeName(firstDestination)}</strong></p>
        <ol>
          {(plan.legs || []).map((leg) => (
            <li key={`${plan.id}-${leg.sequence}`}>
              <span>Arrive {placeName(leg.destination)}</span>
              <b>{formatScheduledTime(leg.estimatedArrival)}</b>
              {leg.dwellSeconds > 0
                ? <small>Stay {(leg.dwellSeconds / 3600).toFixed(1)} h · leave {formatScheduledTime(leg.estimatedNextDeparture)}</small>
                : null}
            </li>
          ))}
        </ol>
        <div className="demand-return">
          <span>{plan.estimatedReturnArrival ? 'Estimated home' : 'Estimated final exit'}</span>
          <strong>{formatScheduledTime(
            plan.estimatedReturnArrival || plan.estimatedFinalArrival,
          )}</strong>
          <small>{placeName(finalDestination)}</small>
        </div>
      </div>
    </details>
  )
}

function DemandSchedulePanel({
  schedule,
  currentTimeOfDaySeconds = 0,
  loading = false,
  onPageChange,
}) {
  const plans = useMemo(() => schedule?.plans || [], [schedule])
  const hours = useMemo(() => {
    const aggregate = schedule?.hourlyPlanCounts
    return Array.isArray(aggregate) && aggregate.length === 24
      ? aggregate.map((count) => Number(count) || 0)
      : hourlyDemandCounts(plans)
  }, [plans, schedule])
  const currentHour = Math.floor(Number(currentTimeOfDaySeconds || 0) / 3600) % 24
  const maximum = Math.max(...hours, 1)
  const totalPlans = Number(schedule?.totalPlans) || 0
  const offset = Math.max(0, Number(schedule?.offset) || 0)
  const limit = Math.max(1, Number(schedule?.limit) || plans.length || 12)
  const returnedPlans = Number.isFinite(Number(schedule?.returnedPlans))
    ? Number(schedule.returnedPlans)
    : plans.length
  const pageCount = Math.max(1, Math.ceil(totalPlans / limit))
  const page = Math.min(pageCount, Math.floor(offset / limit) + 1)
  const rangeStart = returnedPlans > 0 ? offset + 1 : 0
  const rangeEnd = returnedPlans > 0 ? Math.min(totalPlans, offset + returnedPlans) : 0
  const purposeCounts = Object.entries(schedule?.purposeCounts || {})
  const vehicleTypeCounts = Object.entries(schedule?.vehicleTypeCounts || {})

  return (
    <details className="rail-section demand-schedule" open>
      <summary><span>24-hour demand</span><b>{totalPlans} automatic trip plans</b></summary>
      <div className="section-body">
        <p className="demand-method">
          Vehicles depart automatically as the simulation clock reaches their
          plan. The bars are a read-only daily profile; green marks the current
          hour. Arrival/return clocks are estimates—lights, queues and incidents
          determine actual times.
        </p>
        <div className="demand-hours" aria-label="Planned departures by hour">
          {hours.map((count, index) => (
            <div key={index}
              className={index === currentHour ? 'current' : ''}
              aria-label={`${String(index).padStart(2, '0')}:00: ${count} departures`}
              title={`${String(index).padStart(2, '0')}:00 · ${count} departures`}>
              <i style={{ height: `${Math.max(4, count / maximum * 100)}%` }} />
              <span>{String(index).padStart(2, '0')}</span>
            </div>
          ))}
        </div>
        {purposeCounts.length || vehicleTypeCounts.length ? (
          <div className="demand-aggregates" aria-label="Whole-day demand breakdown">
            {vehicleTypeCounts.length ? <p><strong>Vehicle mix</strong>
              <span>{vehicleTypeCounts.map(([type, count]) => (
                `${humanEnum(type)} ${count}`
              )).join(' · ')}</span>
            </p> : null}
            {purposeCounts.length ? <p><strong>Trip purposes</strong>
              <span>{purposeCounts.map(([purpose, count]) => (
                `${humanEnum(purpose)} ${count}`
              )).join(' · ')}</span>
            </p> : null}
          </div>
        ) : null}
        <p className="demand-page-note">
          Inspecting plans {rangeStart}–{rangeEnd} of {totalPlans}. This bounded
          list is route-estimate detail only; all departures still spawn
          automatically.
        </p>
        {plans.length ? <div className="demand-plan-list">
          {plans.map((plan) => <PlanCard key={plan.id} plan={plan} />)}
        </div> : <p className="empty-copy">No plan records on this page.</p>}
        {totalPlans > limit ? <div className="demand-pagination">
          <button disabled={loading || offset === 0 || !onPageChange}
            onClick={() => onPageChange(Math.max(0, offset - limit), limit)}>
            Previous
          </button>
          <span>{loading ? 'Loading…' : `${page} / ${pageCount}`}</span>
          <button disabled={loading || offset + returnedPlans >= totalPlans || !onPageChange}
            onClick={() => onPageChange(offset + limit, limit)}>
            Next
          </button>
        </div> : null}
        {schedule?.failedPlans > 0 ? <p className="demand-warning">
          {schedule.failedPlans} plans could not be resolved; see the API schedule for their IDs.
        </p> : null}
      </div>
    </details>
  )
}

export default memo(DemandSchedulePanel)
