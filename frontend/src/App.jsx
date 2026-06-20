import { useState } from 'react'

const API_BASE = 'http://localhost:7070/api'

const PREFERENCES = [
  { value: 'SHORTEST_TIME', label: 'Shortest Time (Dijkstra)' },
  { value: 'SHORTEST_DISTANCE', label: 'Shortest Distance (Dijkstra)' },
  { value: 'CONGESTION_AWARE', label: 'Congestion Aware (Dijkstra)' },
  { value: 'MINIMUM_HOPS', label: 'Minimum Hops (BFS)' },
]

export default function App() {
  const [start, setStart] = useState('')
  const [destination, setDestination] = useState('')
  const [preference, setPreference] = useState('SHORTEST_TIME')
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)

  const findRoute = async () => {
    if (!start.trim() || !destination.trim()) {
      setError('Please enter both start and destination intersection IDs.')
      return
    }

    setLoading(true)
    setError(null)
    setResult(null)

    try {
      const response = await fetch(`${API_BASE}/routes`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          startIntersectionId: start.trim(),
          destinationIntersectionId: destination.trim(),
          preference,
        }),
      })

      const data = await response.json()

      if (!response.ok) {
        setError(data.error || `Error ${response.status}: ${response.statusText}`)
        return
      }

      setResult(data)
    } catch (err) {
      setError(`Cannot connect to backend. Is the server running on port 7070?\n${err.message}`)
    } finally {
      setLoading(false)
    }
  }

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') findRoute()
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1>Traffic Route Finder</h1>
        <p className="subtitle">Small City Network — 6 intersections (I1–I6)</p>
      </header>

      <main className="main-content">
        <section className="form-section">
          <div className="form-row">
            <div className="form-group">
              <label htmlFor="start">Start Intersection</label>
              <input
                id="start"
                type="text"
                value={start}
                onChange={(e) => setStart(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. I1"
              />
            </div>

            <div className="arrow">→</div>

            <div className="form-group">
              <label htmlFor="destination">Destination Intersection</label>
              <input
                id="destination"
                type="text"
                value={destination}
                onChange={(e) => setDestination(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. I6"
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="preference">Route Preference</label>
            <select
              id="preference"
              value={preference}
              onChange={(e) => setPreference(e.target.value)}
            >
              {PREFERENCES.map((p) => (
                <option key={p.value} value={p.value}>
                  {p.label}
                </option>
              ))}
            </select>
          </div>

          <button className="find-btn" onClick={findRoute} disabled={loading}>
            {loading ? 'Finding Route…' : 'Find Route'}
          </button>
        </section>

        {error && (
          <div className="error-box">
            <strong>Error:</strong> {error}
          </div>
        )}

        {result && (
          <section className="result-section">
            <h2>Route Found</h2>

            <div className="stats-grid">
              <div className="stat-card">
                <span className="stat-label">Algorithm</span>
                <span className="stat-value algo">{result.algorithm}</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Total Distance</span>
                <span className="stat-value">{result.totalDistance.toFixed(2)} km</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Travel Time</span>
                <span className="stat-value">{(result.estimatedTravelTime * 60).toFixed(1)} min</span>
              </div>
              <div className="stat-card">
                <span className="stat-label">Total Cost</span>
                <span className="stat-value">{result.totalCost.toFixed(4)}</span>
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
                        <td>{step.cost.toFixed(4)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="no-steps">Start and destination are the same — no steps needed.</p>
            )}
          </section>
        )}

        <section className="hint-section">
          <h3>Available Intersections</h3>
          <div className="intersection-list">
            {[
              { id: 'I1', name: 'Campus Gate' },
              { id: 'I2', name: 'School Junction' },
              { id: 'I3', name: 'Central Market' },
              { id: 'I4', name: 'Riverside Homes' },
              { id: 'I5', name: 'General Hospital' },
              { id: 'I6', name: 'Night District' },
            ].map((i) => (
              <span key={i.id} className="intersection-tag">
                <strong>{i.id}</strong> — {i.name}
              </span>
            ))}
          </div>
        </section>
      </main>
    </div>
  )
}
