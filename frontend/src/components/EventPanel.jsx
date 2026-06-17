/**
 * Event Panel Component
 * Displays and manages traffic events
 * Uses OOP TrafficEvent model
 */
export default function EventPanel({ simulationHook, appStatus }) {
  const { simulationState, getActiveEvents } = simulationHook

  if (!simulationState) {
    return (
      <div className="event-panel">
        <h2>Traffic Events</h2>
        <p className="no-data">No simulation loaded</p>
      </div>
    )
  }

  const activeEvents = getActiveEvents()
  const allEvents = simulationState.trafficEvents

  return (
    <div className="event-panel">
      <h2>Traffic Events</h2>

      {/* Active Events Summary */}
      <div className="event-section">
        <h3>Active Events ({activeEvents.length})</h3>
        {activeEvents.length === 0 ? (
          <p className="no-data">No active events</p>
        ) : (
          <div className="events-list">
            {activeEvents.map((event) => (
              <div
                key={event.id}
                className="event-item"
                style={{ borderLeft: `4px solid ${event.getTypeColor()}` }}
              >
                <div className="event-header">
                  <span className="event-type">{event.getTypeName()}</span>
                  <span className="event-id">{event.id}</span>
                </div>
                <div className="event-details">
                  <p>
                    <strong>Road:</strong> {event.affectedRoadId}
                  </p>
                  <div>
                    <strong>Progress:</strong>
                    <div className="progress-bar">
                      <div
                        className="progress-fill"
                        style={{ width: `${event.getProgressPercentage(appStatus.currentTick)}%` }}
                      />
                    </div>
                  </div>
                  <p>
                    <strong>Time Remaining:</strong> {event.getTimeRemaining(appStatus.currentTick)} ticks
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* All Events */}
      <div className="event-section">
        <h3>All Events ({allEvents.length})</h3>
        <table className="events-table">
          <thead>
            <tr>
              <th>Type</th>
              <th>Road</th>
              <th>Start Tick</th>
              <th>Duration</th>
              <th>Status</th>
              <th>Progress</th>
            </tr>
          </thead>
          <tbody>
            {allEvents.map((event) => (
              <tr key={event.id}>
                <td>
                  <span
                    className="event-badge"
                    style={{ backgroundColor: event.getTypeColor() }}
                  >
                    {event.getTypeName()}
                  </span>
                </td>
                <td>{event.affectedRoadId}</td>
                <td>{event.startTick}</td>
                <td>{event.durationTicks}</td>
                <td>{event.getStatusName()}</td>
                <td>
                  <div className="progress-bar-small">
                    <div
                      className="progress-fill"
                      style={{ width: `${event.getProgressPercentage(appStatus.currentTick)}%` }}
                    />
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* Event Statistics */}
      <div className="event-section">
        <h3>Event Statistics</h3>
        <div className="stats-grid">
          {['CONGESTION', 'ACCIDENT', 'ROAD_CLOSURE', 'TRAFFIC_LIGHT_DELAY'].map((type) => (
            <div key={type} className="stat-card">
              <span className="stat-label">{type}</span>
              <span className="stat-value">
                {allEvents.filter((e) => e.type === type).length}
              </span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
