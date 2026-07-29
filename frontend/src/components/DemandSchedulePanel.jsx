import { memo, useEffect, useMemo, useState } from 'react'
import { formatScheduledTime, hourlyDemandCounts } from './demandScheduleUtils'

const PAGE_SIZE = 12

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

function DemandSchedulePanel({ schedule, currentTimeOfDaySeconds = 0 }) {
  const plans = useMemo(() => schedule?.plans || [], [schedule])
  const [purpose, setPurpose] = useState('ALL')
  const [hour, setHour] = useState('ALL')
  const [page, setPage] = useState(0)
  const hours = useMemo(() => hourlyDemandCounts(plans), [plans])
  const purposes = useMemo(
    () => [...new Set(plans.map((plan) => plan.purpose))].sort(),
    [plans],
  )
  // Filters describe one concrete scenario schedule. Carrying them across a
  // scenario switch can leave the select controlled by an option that no
  // longer exists and make a valid new schedule look empty.
  useEffect(() => {
    setPurpose('ALL')
    setHour('ALL')
    setPage(0)
  }, [schedule])
  const filtered = useMemo(() => plans.filter((plan) => {
    const planHour = Math.floor(Number(plan.departure?.timeOfDaySeconds || 0) / 3600)
    return (purpose === 'ALL' || plan.purpose === purpose)
      && (hour === 'ALL' || planHour === Number(hour))
  }), [hour, plans, purpose])
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, pageCount - 1)
  const visible = filtered.slice(safePage * PAGE_SIZE, (safePage + 1) * PAGE_SIZE)
  const currentHour = Math.floor(Number(currentTimeOfDaySeconds || 0) / 3600) % 24
  const maximum = Math.max(...hours, 1)

  return (
    <details className="rail-section demand-schedule" open>
      <summary><span>24-hour demand</span><b>{schedule?.totalPlans || 0} planned vehicles</b></summary>
      <div className="section-body">
        <p className="demand-method">
          Seeded 24-hour proxy informed by historical HOUTRANS patterns—not
          observed District 1 counts. Arrival/return clocks are free-flow
          estimates; lights, queues and incidents determine actual times.
        </p>
        <div className="demand-hours" aria-label="Planned departures by hour">
          {hours.map((count, index) => (
            <button key={index} type="button"
              className={`${index === currentHour ? 'current' : ''} ${String(index) === hour ? 'selected' : ''}`}
              aria-label={`${String(index).padStart(2, '0')}:00: ${count} departures`}
              title={`${String(index).padStart(2, '0')}:00 · ${count} departures`}
              onClick={() => {
                setHour(String(index) === hour ? 'ALL' : String(index))
                setPage(0)
              }}>
              <i style={{ height: `${Math.max(4, count / maximum * 100)}%` }} />
              <span>{String(index).padStart(2, '0')}</span>
            </button>
          ))}
        </div>
        <div className="two-fields demand-filters">
          <label className="field"><span>Purpose</span><select value={purpose}
            onChange={(event) => {
              setPurpose(event.target.value)
              setPage(0)
            }}>
            <option value="ALL">All purposes</option>
            {purposes.map((item) => <option key={item} value={item}>{humanEnum(item)}</option>)}
          </select></label>
          <label className="field"><span>Hour</span><select value={hour}
            onChange={(event) => {
              setHour(event.target.value)
              setPage(0)
            }}>
            <option value="ALL">All 24 hours</option>
            {hours.map((count, index) => (
              <option key={index} value={index}>
                {String(index).padStart(2, '0')}:00 · {count}
              </option>
            ))}
          </select></label>
        </div>
        {visible.length ? <div className="demand-plan-list">
          {visible.map((plan) => <PlanCard key={plan.id} plan={plan} />)}
        </div> : <p className="empty-copy">No planned departures match this filter.</p>}
        {filtered.length > PAGE_SIZE ? <div className="demand-pagination">
          <button disabled={safePage === 0} onClick={() => setPage((value) => value - 1)}>Previous</button>
          <span>{safePage + 1} / {pageCount} · {filtered.length} plans</span>
          <button disabled={safePage + 1 >= pageCount}
            onClick={() => setPage((value) => value + 1)}>Next</button>
        </div> : null}
        {schedule?.failedPlans > 0 ? <p className="demand-warning">
          {schedule.failedPlans} plans could not be resolved; see the API schedule for their IDs.
        </p> : null}
      </div>
    </details>
  )
}

export default memo(DemandSchedulePanel)
