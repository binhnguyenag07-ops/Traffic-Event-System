import { ROAD_STATUS_COLOR, congestionColor } from '../theme'

/**
 * Traffic summary, road-congestion heatmap, and algorithm cards.
 *
 * Purely presentational: traffic/algorithms come from useStatistics (or the
 * snapshot), roads from the shared road state, so everything shows one tick.
 */
export default function StatisticsPanel({
  traffic = null,
  algorithms = [],
  roads = [],
  loading = false,
  error = null,
  onRefresh = () => {},
}) {
  const maxCongestion =
    roads.length > 0 ? Math.max(...roads.map((r) => r.congestionLevel ?? 0)) : 0

  return (
    <section className="sim-panel">
      <h2>Statistics &amp; Heatmap</h2>

      <div className="sim-row">
        <button className="sim-btn secondary" onClick={onRefresh} disabled={loading}>
          {loading ? 'Loading…' : 'Refresh'}
        </button>
        {traffic && (
          <span className="sim-hint">
            Tick&nbsp;<strong>{traffic.currentTick}</strong>
          </span>
        )}
      </div>

      {error && <div className="sim-error">Statistics: {error}</div>}

      {traffic && (
        <>
          <h3>Traffic Summary</h3>
          <div className="stats-grid">
            <div className="stat-card">
              <span className="stat-label">Total Vehicles</span>
              <span className="stat-value">{traffic.totalVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Arrived</span>
              <span className="stat-value" style={{ color: '#16a34a' }}>{traffic.arrivedVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Moving</span>
              <span className="stat-value" style={{ color: '#2563eb' }}>{traffic.movingVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Blocked</span>
              <span className="stat-value" style={{ color: '#dc2626' }}>{traffic.blockedVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Waiting</span>
              <span className="stat-value" style={{ color: '#64748b' }}>{traffic.waitingVehicles}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Avg Congestion</span>
              <span
                className="stat-value"
                style={{ color: congestionColor(traffic.averageCongestionLevel) }}
              >
                {(traffic.averageCongestionLevel * 100).toFixed(1)}%
              </span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Congested Roads</span>
              <span className="stat-value" style={{ color: '#d97706' }}>{traffic.congestedRoadCount}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Blocked Roads</span>
              <span className="stat-value" style={{ color: '#dc2626' }}>{traffic.blockedRoadCount}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Closed Roads</span>
              <span className="stat-value" style={{ color: '#991b1b' }}>{traffic.closedRoadCount}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Active Events</span>
              <span className="stat-value" style={{ color: '#6366f1' }}>{traffic.activeEventCount}</span>
            </div>
          </div>
        </>
      )}

      {roads.length > 0 && (
        <>
          <h3>Road Congestion Heatmap</h3>
          <div className="vehicle-table-wrap">
            <table className="steps-table">
              <thead>
                <tr>
                  <th>Road</th>
                  <th>From → To</th>
                  <th>Status</th>
                  <th>Congestion</th>
                  <th style={{ width: '160px' }}>Heat</th>
                </tr>
              </thead>
              <tbody>
                {roads
                  .slice()
                  .sort((a, b) => (b.congestionLevel ?? 0) - (a.congestionLevel ?? 0))
                  .map((r) => {
                    const level = r.congestionLevel ?? 0
                    const pct = maxCongestion > 0 ? (level / maxCongestion) * 100 : 0
                    const color = congestionColor(level)
                    return (
                      <tr key={r.id}>
                        <td><code>{r.id}</code></td>
                        <td>
                          <code>{r.startIntersectionId}</code>
                          {' → '}
                          <code>{r.endIntersectionId}</code>
                        </td>
                        <td>
                          <span
                            className="v-status"
                            style={{ color: ROAD_STATUS_COLOR[r.status] ?? '#64748b' }}
                          >
                            {r.status}
                          </span>
                        </td>
                        <td style={{ color, fontWeight: 700 }}>{(level * 100).toFixed(1)}%</td>
                        <td>
                          <div style={{ background: '#f1f5f9', borderRadius: 4, height: 10, overflow: 'hidden' }}>
                            <div
                              style={{
                                width: `${pct}%`,
                                height: '100%',
                                background: color,
                                borderRadius: 4,
                                transition: 'width 0.3s',
                              }}
                            />
                          </div>
                        </td>
                      </tr>
                    )
                  })}
              </tbody>
            </table>
          </div>
        </>
      )}

      {algorithms.length > 0 && (
        <>
          <h3>Routing Algorithms</h3>
          <div className="stats-grid">
            {algorithms.map((a) => (
              <div className="stat-card" key={a.algorithmName}>
                <span className="stat-label">{a.algorithmName}</span>
                <span className="stat-value algo">
                  {a.supportedPreferences?.join(', ') ?? '—'}
                </span>
              </div>
            ))}
          </div>
        </>
      )}
    </section>
  )
}
