import { useState, useEffect, useCallback } from 'react'
import { trafficEventApi } from '../api/trafficAPI'
import { EVENT_TYPES, EVENT_TYPE_COLOR, EVENT_STATUS_COLOR } from '../theme'

/**
 * Traffic event dispatch and management.
 *
 * Props:
 *   roads      — road list for the road selector/filter
 *   tick       — current simulation tick; the list re-syncs when it changes
 *   onChanged  — called after any create/update/delete so the app can refresh
 *                the snapshot (map colors, statistics) in the same breath
 */
export default function EventPanel({ roads = [], tick = 0, onChanged = () => {} }) {
  const [events, setEvents] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [type, setType] = useState('CONGESTION')
  const [roadId, setRoadId] = useState('')
  const [startTick, setStartTick] = useState(0)
  const [durationTicks, setDurationTicks] = useState(5)
  const [value, setValue] = useState('')
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [roadFilter, setRoadFilter] = useState('ALL')

  const fetchEvents = useCallback(async () => {
    try {
      let data
      if (roadFilter !== 'ALL') {
        data = await trafficEventApi.getEventsOnRoad(roadFilter)
      } else {
        data = await trafficEventApi.getAllEvents()
      }
      let list = Array.isArray(data) ? data : []
      if (statusFilter !== 'ALL') {
        list = list.filter((ev) => ev.status === statusFilter)
      }
      setEvents(list)
    } catch {
      /* list stays as-is when the fetch fails; errors surface on actions */
    }
  }, [statusFilter, roadFilter])

  useEffect(() => {
    fetchEvents()
  }, [fetchEvents, tick])

  const act = async (fn) => {
    setError(null)
    try {
      await fn()
      await fetchEvents()
      onChanged()
    } catch (e) {
      setError(e.message)
    }
  }

  const handleCreate = async () => {
    if (!roadId) {
      setError('Please select a road')
      return
    }
    setLoading(true)
    await act(async () => {
      const eventData = value !== '' ? { value: parseFloat(value) } : {}
      await trafficEventApi.createEvent(type, roadId, Number(startTick), Number(durationTicks), eventData)
      setRoadId('')
      setValue('')
    })
    setLoading(false)
  }

  return (
    <section className="sim-panel">
      <h2>Event Controls</h2>

      <div className="form-row" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div className="form-group">
          <label htmlFor="ep-type">Event Type</label>
          <select id="ep-type" value={type} onChange={(e) => setType(e.target.value)}>
            {EVENT_TYPES.map((t) => (
              <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label htmlFor="ep-road">Road</label>
          <select id="ep-road" value={roadId} onChange={(e) => setRoadId(e.target.value)}>
            <option value="">Select a road…</option>
            {roads.map((r) => (
              <option key={r.id} value={r.id}>
                {r.id} ({r.startIntersectionId} → {r.endIntersectionId})
              </option>
            ))}
          </select>
        </div>
        <div className="form-group" style={{ flex: '0 0 90px' }}>
          <label htmlFor="ep-start">Start Tick</label>
          <input
            id="ep-start"
            type="number"
            min={0}
            value={startTick}
            onChange={(e) => setStartTick(e.target.value)}
          />
        </div>
        <div className="form-group" style={{ flex: '0 0 90px' }}>
          <label htmlFor="ep-duration">Duration</label>
          <input
            id="ep-duration"
            type="number"
            min={1}
            value={durationTicks}
            onChange={(e) => setDurationTicks(e.target.value)}
          />
        </div>
        <div className="form-group" style={{ flex: '0 0 90px' }}>
          <label htmlFor="ep-value">Value</label>
          <input
            id="ep-value"
            type="number"
            min={0}
            step={0.1}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            placeholder="0.5"
          />
        </div>
      </div>

      <div className="sim-row">
        <button className="sim-btn amber" onClick={handleCreate} disabled={loading}>
          + Dispatch Event
        </button>
        <button className="sim-btn secondary" onClick={() => fetchEvents()}>
          Refresh
        </button>
        <span className="sim-hint">
          Value: congestion increase (0–1), capacity reduction (0–1), or delay seconds
        </span>
      </div>

      <div className="sim-row">
        <div className="form-group" style={{ flex: '0 0 160px' }}>
          <label htmlFor="ep-filter-status">Filter Status</label>
          <select
            id="ep-filter-status"
            value={statusFilter}
            onChange={(e) => setStatusFilter(e.target.value)}
          >
            <option value="ALL">All</option>
            <option value="SCHEDULED">Scheduled</option>
            <option value="ACTIVE">Active</option>
            <option value="RESOLVED">Resolved</option>
          </select>
        </div>
        <div className="form-group" style={{ flex: '0 0 220px' }}>
          <label htmlFor="ep-filter-road">Filter Road</label>
          <select
            id="ep-filter-road"
            value={roadFilter}
            onChange={(e) => setRoadFilter(e.target.value)}
          >
            <option value="ALL">All roads</option>
            {roads.map((r) => (
              <option key={r.id} value={r.id}>{r.id}</option>
            ))}
          </select>
        </div>
      </div>

      {error && <div className="sim-error">{error}</div>}

      {events.length > 0 ? (
        <div className="vehicle-table-wrap">
          <h3>Events ({events.length})</h3>
          <table className="steps-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Type</th>
                <th>Road</th>
                <th>Start</th>
                <th>Duration</th>
                <th>Status</th>
                <th>Actions</th>
              </tr>
            </thead>
            <tbody>
              {events.map((ev) => (
                <tr key={ev.id}>
                  <td><code>{ev.id}</code></td>
                  <td>
                    <span className="v-status" style={{ color: EVENT_TYPE_COLOR[ev.type] ?? '#64748b' }}>
                      {ev.type?.replace(/_/g, ' ')}
                    </span>
                  </td>
                  <td><code>{ev.affectedRoadId}</code></td>
                  <td>{ev.startTick}</td>
                  <td>{ev.durationTicks}</td>
                  <td>
                    <span className="v-status" style={{ color: EVENT_STATUS_COLOR[ev.status] ?? '#64748b' }}>
                      {ev.status}
                    </span>
                  </td>
                  <td>
                    {ev.status === 'SCHEDULED' && (
                      <button
                        className="sim-btn-xs green"
                        onClick={() => act(() => trafficEventApi.updateEventStatus(ev.id, 'ACTIVE'))}
                      >
                        Activate
                      </button>
                    )}
                    {ev.status !== 'RESOLVED' && (
                      <button
                        className="sim-btn-xs amber"
                        onClick={() => act(() => trafficEventApi.updateEventStatus(ev.id, 'RESOLVED'))}
                      >
                        Resolve
                      </button>
                    )}
                    <button
                      className="sim-btn-xs red"
                      onClick={() => act(() => trafficEventApi.removeEvent(ev.id))}
                    >
                      Delete
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="sim-hint" style={{ marginTop: '0.5rem' }}>
          No events match the current filters. Dispatch a traffic event above — it will activate
          when the simulation reaches its start tick.
        </p>
      )}
    </section>
  )
}
