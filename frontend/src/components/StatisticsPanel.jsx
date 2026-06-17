export default function StatisticsPanel({ statisticsHook }) {
  const {
    trafficStats,
    algorithmStats,
    selectedAlgorithm,
    loading,
    error,
    autoRefresh,
    setAutoRefresh,
    loadTrafficStatistics,
    exportStatisticsReport,
  } = statisticsHook

  const handleExportReport = async (format = 'json') => {
    await exportStatisticsReport(format)
  }

  return (
    <div className="statistics-panel">
      <h2>Statistics</h2>

      <div className="stats-controls">
        <button onClick={loadTrafficStatistics} disabled={loading} className="btn-secondary">
          {loading ? 'Refreshing...' : 'Refresh'}
        </button>
        <label htmlFor="autorefresh-toggle" className="toggle-label">
          <input
            id="autorefresh-toggle"
            type="checkbox"
            checked={autoRefresh}
            onChange={(e) => setAutoRefresh(e.target.checked)}
          />
          Auto-refresh
        </label>
        <button onClick={() => handleExportReport('json')} className="btn-secondary">
          Export JSON
        </button>
      </div>

      <div className="traffic-statistics">
        <h3>Traffic Statistics</h3>
        {trafficStats ? (
          <div className="stats-grid">
            <div className="stat-card">
              <div className="stat-label">Total Vehicles</div>
              <div className="stat-value">{trafficStats.totalVehicles}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Arrived Vehicles</div>
              <div className="stat-value">{trafficStats.arrivedVehicles}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Avg Travel Time</div>
              <div className="stat-value">
                {trafficStats.averageTravelTime.toFixed(2)} ticks
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Avg Congestion Level</div>
              <div className="stat-value">
                {trafficStats.getAverageCongestionPercentage().toFixed(1)}%
              </div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Blocked Roads</div>
              <div className="stat-value">{trafficStats.blockedRoadCount}</div>
            </div>
            <div className="stat-card">
              <div className="stat-label">Success Rate</div>
              <div className="stat-value">{trafficStats.getSuccessRate().toFixed(1)}%</div>
            </div>
          </div>
        ) : (
          <p className="no-data">No traffic statistics available</p>
        )}
      </div>

      <div className="algorithm-statistics">
        <h3>Algorithm Statistics</h3>
        {algorithmStats.length > 0 ? (
          <div className="algorithm-cards">
            {algorithmStats.map((stats) => (
              <div key={stats.algorithmName || selectedAlgorithm} className="algorithm-card">
                <h4>{(stats.algorithmName || selectedAlgorithm)?.toUpperCase()}</h4>
                <div className="stats-list">
                  <div className="stat-row">
                    <span className="label">Avg Computation Time:</span>
                    <span className="value">{stats.averageComputationTime} ms</span>
                  </div>
                  <div className="stat-row">
                    <span className="label">Route Recalculations:</span>
                    <span className="value">{stats.routeRecalculationCount}</span>
                  </div>
                  <div className="stat-row">
                    <span className="label">Avg Route Cost:</span>
                    <span className="value">{stats.averageRouteCost.toFixed(2)}</span>
                  </div>
                  <div className="stat-row">
                    <span className="label">Successful Routes:</span>
                    <span className="value">{stats.successfulRoutes}</span>
                  </div>
                  <div className="stat-row">
                    <span className="label">Failed Routes:</span>
                    <span className="value">{stats.failedRoutes}</span>
                  </div>
                  <div className="stat-row">
                    <span className="label">Success Rate:</span>
                    <span className="value">{stats.getSuccessRate().toFixed(1)}%</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <p className="no-data">No algorithm statistics available</p>
        )}
      </div>

      {error && <div className="error-message">{error}</div>}
    </div>
  )
}
