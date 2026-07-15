import { useState } from 'react'
import { useAlgorithms, ROUTE_PREFERENCES } from '../hooks/useAlgorithms'

/**
 * Route finder. Start/destination come from the live intersection list
 * (nothing hard-coded); the found route is lifted via onRouteFound so the
 * map can draw the highlight overlay.
 */
export default function AlgorithmPanel({ intersections = [], onRouteFound = () => {} }) {
  const { preference, setPreference, result, loading, error, findRoute, clearResult } =
    useAlgorithms()
  const [startId, setStartId] = useState('')
  const [destId, setDestId] = useState('')

  const handleFind = async () => {
    if (!startId || !destId) return
    const data = await findRoute(startId, destId)
    if (data) onRouteFound(data)
  }

  const handleClear = () => {
    clearResult()
    onRouteFound(null)
  }

  return (
    <section className="sim-panel">
      <h2>Route Finder</h2>

      <div className="form-row" style={{ flexWrap: 'wrap', alignItems: 'flex-end' }}>
        <div className="form-group">
          <label htmlFor="ap-start">Start Intersection</label>
          <select id="ap-start" value={startId} onChange={(e) => setStartId(e.target.value)}>
            <option value="">Select…</option>
            {intersections.map((i) => (
              <option key={i.id} value={i.id}>
                {i.id} — {i.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label htmlFor="ap-dest">Destination Intersection</label>
          <select id="ap-dest" value={destId} onChange={(e) => setDestId(e.target.value)}>
            <option value="">Select…</option>
            {intersections.map((i) => (
              <option key={i.id} value={i.id}>
                {i.id} — {i.name}
              </option>
            ))}
          </select>
        </div>
        <div className="form-group">
          <label htmlFor="ap-pref">Route Preference</label>
          <select id="ap-pref" value={preference} onChange={(e) => setPreference(e.target.value)}>
            {ROUTE_PREFERENCES.map((p) => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        </div>
      </div>

      <div className="sim-row">
        <button
          className="sim-btn primary"
          onClick={handleFind}
          disabled={loading || !startId || !destId}
        >
          {loading ? 'Finding Route…' : 'Find Route'}
        </button>
        {result && (
          <button className="sim-btn secondary" onClick={handleClear}>
            Clear Route
          </button>
        )}
      </div>

      {error && (
        <div className="sim-error">
          <strong>Error:</strong> {error}
        </div>
      )}

      {result && (
        <>
          <h3>Route Found</h3>
          <div className="stats-grid">
            <div className="stat-card">
              <span className="stat-label">Algorithm</span>
              <span className="stat-value algo">{result.algorithm}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Total Distance</span>
              <span className="stat-value">{result.totalDistance?.toFixed(2)} km</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Travel Time</span>
              <span className="stat-value">{(result.estimatedTravelTime * 60).toFixed(1)} min</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Total Cost</span>
              <span className="stat-value">{result.totalCost?.toFixed(4)}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Visited Nodes</span>
              <span className="stat-value">{result.visitedNodes}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Computation</span>
              <span className="stat-value">{result.computationTimeMillis} ms</span>
            </div>
          </div>

          <h3>Steps ({result.steps?.length ?? 0})</h3>
          {result.steps && result.steps.length > 0 ? (
            <div className="steps-wrapper">
              <table className="steps-table">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>From</th>
                    <th>To</th>
                    <th>Road ID</th>
                    <th>Cost</th>
                  </tr>
                </thead>
                <tbody>
                  {result.steps.map((step, i) => (
                    <tr key={i}>
                      <td className="step-num">{i + 1}</td>
                      <td><code>{step.from}</code></td>
                      <td><code>{step.to}</code></td>
                      <td><code>{step.roadId}</code></td>
                      <td>{step.cost?.toFixed(4)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="no-steps">Start and destination are the same — no steps needed.</p>
          )}
        </>
      )}
    </section>
  )
}
