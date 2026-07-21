import { memo, useEffect, useMemo, useState } from 'react'
import { experimentApi } from '../api/trafficAPI'

function ExperimentPanel() {
  const [report, setReport] = useState(null)
  const [scenarioId, setScenarioId] = useState('normal')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    experimentApi.latest().then(setReport).catch((nextError) => setError(nextError.message))
  }, [])

  const scenario = useMemo(
    () => report?.scenarios?.find((item) => item.id === scenarioId) || report?.scenarios?.[0],
    [report, scenarioId],
  )
  const maxVisited = useMemo(
    () => Math.max(1, ...(scenario?.algorithms || []).map((item) => Number(item.averageVisitedNodes || 0))),
    [scenario],
  )

  const rerun = async () => {
    setBusy(true)
    setError('')
    try {
      setReport(await experimentApi.run())
    } catch (nextError) {
      setError(nextError.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <details className="rail-section experiment-panel">
      <summary><span>Experiments</span><b>{report ? `seed ${report.seed}` : 'Loading baseline'}</b></summary>
      <div className="section-body">
        <div className="experiment-toolbar">
          <label className="field"><span>Traffic condition</span>
            <select value={scenario?.id || scenarioId} onChange={(event) => setScenarioId(event.target.value)}>
              {(report?.scenarios || []).map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <button disabled={busy} onClick={rerun}>{busy ? 'Running…' : 'Run again'}</button>
        </div>
        {scenario ? <>
          <p className="experiment-caption">{scenario.intersectionCount} junctions · {scenario.roadCount} roads · {report.requestsPerScenario} requests</p>
          <div className="experiment-bars" aria-label={`${scenario.name} visited nodes comparison`}>
            {scenario.algorithms.map((item) => <div key={item.algorithm}>
              <span>{item.algorithm}</span>
              <i><b style={{ width: `${item.averageVisitedNodes / maxVisited * 100}%` }} /></i>
              <strong>{item.averageVisitedNodes.toFixed(1)}</strong>
            </div>)}
          </div>
          <div className="experiment-table-wrap"><table className="experiment-table">
            <thead><tr><th>Algorithm</th><th>Objective</th><th>Success</th><th>Time</th><th>Optimal</th></tr></thead>
            <tbody>{scenario.algorithms.map((item) => <tr key={item.algorithm}>
              <th>{item.algorithm}</th><td>{item.objective.replaceAll('_', ' ')}</td>
              <td>{item.successRate.toFixed(0)}%</td><td>{item.averageComputationTimeMillis.toFixed(3)} ms</td>
              <td>{item.averagePathOptimality.toFixed(1)}%</td>
            </tr>)}</tbody>
          </table></div>
        </> : <p className="empty-copy">Experiment results are not available yet.</p>}
        {error ? <p className="editor-notice" role="alert">{error}</p> : null}
      </div>
    </details>
  )
}

export default memo(ExperimentPanel)
