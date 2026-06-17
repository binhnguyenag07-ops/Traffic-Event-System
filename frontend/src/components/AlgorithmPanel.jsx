import { useState, useEffect } from 'react'

/**
 * Algorithm Panel Component
 * Displays routing algorithms and their statistics
 * Uses OOP AlgorithmStatistics model
 */
export default function AlgorithmPanel({
  algorithmsHook,
  roadNetworkHook,
  onFindRoute,
}) {
  const {
    algorithms,
    selectedAlgorithm,
    setSelectedAlgorithm,
    algorithmStats,
    loading,
    error,
    getRanking,
    compareAlgorithms,
  } = algorithmsHook

  const { intersections } = roadNetworkHook

  const [startIntersection, setStartIntersection] = useState(null)
  const [endIntersection, setEndIntersection] = useState(null)
  const [routeResult, setRouteResult] = useState(null)
  const [comparing, setComparing] = useState(false)
  const [comparisonResults, setComparisonResults] = useState(null)

  /**
   * Initialize default intersections
   */
  useEffect(() => {
    if (intersections && intersections.length > 0) {
      setStartIntersection(intersections[0])
      setEndIntersection(intersections[Math.min(5, intersections.length - 1)])
    }
  }, [intersections])

  /**
   * Handle find route
   */
  const handleFindRoute = async () => {
    if (!startIntersection || !endIntersection) {
      alert('Please select start and end intersections')
      return
    }

    const route = await onFindRoute(
      startIntersection.id,
      endIntersection.id,
      selectedAlgorithm
    )
    setRouteResult(route)
  }

  /**
   * Handle algorithm comparison
   */
  const handleCompare = async () => {
    if (!startIntersection || !endIntersection) {
      alert('Please select start and end intersections')
      return
    }

    setComparing(true)
    try {
      const results = await compareAlgorithms(
        startIntersection.id,
        endIntersection.id
      )
      setComparisonResults(results)
    } catch (err) {
      console.error('Comparison failed:', err)
    } finally {
      setComparing(false)
    }
  }

  return (
    <div className="algorithm-panel">
      <h2>Routing Algorithms</h2>

      {/* Algorithm Selection */}
      <div className="algorithm-section">
        <h3>Available Algorithms</h3>
        <div className="algorithm-list">
          {algorithms.map((algo) => (
            <button
              key={algo}
              className={`algorithm-button ${selectedAlgorithm === algo ? 'active' : ''}`}
              onClick={() => setSelectedAlgorithm(algo)}
              disabled={loading}
            >
              {algo}
            </button>
          ))}
        </div>
      </div>

      {/* Algorithm Statistics */}
      {algorithmStats && (
        <div className="algorithm-section">
          <h3>Algorithm Statistics: {selectedAlgorithm}</h3>
          <div className="stats-grid">
            <div className="stat-card">
              <span className="stat-label">Average Computation Time:</span>
              <span className="stat-value">
                {algorithmStats.averageComputationTime.toFixed(2)}ms
              </span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Total Routes Computed:</span>
              <span className="stat-value">{algorithmStats.totalRoutesComputed}</span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Average Path Length:</span>
              <span className="stat-value">
                {algorithmStats.averagePathLength.toFixed(2)}
              </span>
            </div>
            <div className="stat-card">
              <span className="stat-label">Efficiency Score:</span>
              <span className="stat-value">
                {algorithmStats.getEfficiencyScore().toFixed(2)}
              </span>
            </div>
          </div>
        </div>
      )}

      {/* Route Finding */}
      <div className="algorithm-section">
        <h3>Find Route</h3>
        <div className="route-controls">
          <div className="control-group">
            <label htmlFor="start-intersection">Start:</label>
            <select
              id="start-intersection"
              value={startIntersection?.id || ''}
              onChange={(e) => {
                const selected = intersections.find((i) => i.id === e.target.value)
                setStartIntersection(selected)
              }}
              disabled={loading}
            >
              <option value="">Select start...</option>
              {intersections.map((int) => (
                <option key={int.id} value={int.id}>
                  {int.name || int.id}
                </option>
              ))}
            </select>
          </div>

          <div className="control-group">
            <label htmlFor="end-intersection">End:</label>
            <select
              id="end-intersection"
              value={endIntersection?.id || ''}
              onChange={(e) => {
                const selected = intersections.find((i) => i.id === e.target.value)
                setEndIntersection(selected)
              }}
              disabled={loading}
            >
              <option value="">Select end...</option>
              {intersections.map((int) => (
                <option key={int.id} value={int.id}>
                  {int.name || int.id}
                </option>
              ))}
            </select>
          </div>

          <button
            onClick={handleFindRoute}
            disabled={!startIntersection || !endIntersection || loading}
            className="btn-primary"
          >
            {loading ? 'Finding...' : '🔍 Find Route'}
          </button>
        </div>
      </div>

      {/* Route Result */}
      {routeResult && (
        <div className="algorithm-section">
          <h3>Route Result</h3>
          <div className="route-result">
            <div className="result-item">
              <span className="result-label">Total Steps:</span>
              <span className="result-value">{routeResult.getLength()}</span>
            </div>
            <div className="result-item">
              <span className="result-label">Total Distance:</span>
              <span className="result-value">{routeResult.totalDistance.toFixed(2)}</span>
            </div>
            <div className="result-item">
              <span className="result-label">Estimated Time:</span>
              <span className="result-value">{routeResult.estimatedTravelTime.toFixed(2)}s</span>
            </div>
            <div className="result-item">
              <span className="result-label">Total Cost:</span>
              <span className="result-value">{routeResult.totalCost.toFixed(2)}</span>
            </div>
          </div>

          <div className="route-steps">
            <h4>Route Steps</h4>
            <div className="steps-list">
              {routeResult.steps.map((step, index) => (
                <div key={index} className="step-item">
                  <span className="step-number">{index + 1}</span>
                  <span className="step-info">
                    {step.fromIntersectionId} → {step.toIntersectionId}
                  </span>
                  <span className="step-cost">Cost: {step.cost.toFixed(2)}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* Algorithm Comparison */}
      <div className="algorithm-section">
        <h3>Compare Algorithms</h3>
        <button
          onClick={handleCompare}
          disabled={!startIntersection || !endIntersection || comparing || loading}
          className="btn-secondary"
        >
          {comparing ? 'Comparing...' : '⚖️ Compare All'}
        </button>

        {comparisonResults && (
          <div className="comparison-results">
            <h4>Comparison Results</h4>
            <table className="comparison-table">
              <thead>
                <tr>
                  <th>Algorithm</th>
                  <th>Steps</th>
                  <th>Distance</th>
                  <th>Time (s)</th>
                  <th>Cost</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(comparisonResults).map(([algo, result]) => (
                  <tr key={algo}>
                    <td>{algo}</td>
                    <td>
                      {result.error ? 'Error' : result.getLength?.()}
                    </td>
                    <td>
                      {result.error ? 'N/A' : result.totalDistance?.toFixed(2)}
                    </td>
                    <td>
                      {result.error ? 'N/A' : result.estimatedTravelTime?.toFixed(2)}
                    </td>
                    <td>
                      {result.error ? 'N/A' : result.totalCost?.toFixed(2)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* Algorithm Ranking */}
      <div className="algorithm-section">
        <h3>Algorithm Ranking</h3>
        <div className="ranking-list">
          {getRanking().map((item, index) => (
            <div key={item.name} className="ranking-item">
              <span className="ranking-position">#{index + 1}</span>
              <span className="ranking-name">{item.name}</span>
              <span className="ranking-score">
                Efficiency: {item.efficiency.toFixed(2)}
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Error Display */}
      {error && (
        <div className="error-message">
          <span className="error-icon">⚠️</span>
          <span>{error}</span>
        </div>
      )}
    </div>
  )
}
