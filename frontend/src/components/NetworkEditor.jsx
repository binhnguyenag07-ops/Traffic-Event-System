import { memo, useEffect, useState } from 'react'

const LOCATION_TYPES = ['NORMAL', 'COMMERCIAL', 'RESIDENTIAL', 'SCHOOL', 'HOSPITAL', 'BAR']
const ROAD_STATUSES = ['OPEN', 'CONGESTED', 'BLOCKED', 'CLOSED']
const ROAD_CLASSES = ['PRIMARY', 'SECONDARY', 'TERTIARY', 'UNCLASSIFIED', 'RESIDENTIAL', 'LIVING_STREET']

function Field({ label, children }) {
  return <label className="field"><span>{label}</span>{children}</label>
}

function NetworkEditor({ dashboard, intersections, status, selected, onSelect,
  captureIntersection, setCaptureIntersection, draftPoint, setDraftPoint, onHighlightsChange }) {
  const [junctionId, setJunctionId] = useState('')
  const [junctionName, setJunctionName] = useState('')
  const [locationType, setLocationType] = useState('NORMAL')
  const [roadId, setRoadId] = useState('')
  const [reverseRoadId, setReverseRoadId] = useState('')
  const [roadStart, setRoadStart] = useState('')
  const [roadEnd, setRoadEnd] = useState('')
  const [speedLimit, setSpeedLimit] = useState(40)
  const [capacity, setCapacity] = useState(60)
  const [roadClass, setRoadClass] = useState('RESIDENTIAL')
  const [bidirectional, setBidirectional] = useState(true)
  const [connectionId, setConnectionId] = useState('')
  const [connections, setConnections] = useState({ incoming: [], outgoing: [] })
  const [conditionStatus, setConditionStatus] = useState('OPEN')
  const [congestion, setCongestion] = useState(0)
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState('')
  const editable = status !== 'RUNNING'
  const selectedRoad = selected?.kind === 'road' ? selected.data : null
  const selectedIntersection = selected?.kind === 'intersection' ? selected.data : null

  useEffect(() => {
    if (!selectedRoad) return
    setConditionStatus(selectedRoad.status || 'OPEN')
    setCongestion(Math.round(Number(selectedRoad.congestionLevel || 0) * 100))
  }, [selectedRoad])

  const perform = async (action, successMessage) => {
    setBusy(true)
    setNotice('')
    try {
      const result = await action()
      setNotice(successMessage)
      return result
    } catch (error) {
      setNotice(error.message)
      return null
    } finally {
      setBusy(false)
    }
  }

  const addJunction = async () => {
    if (!draftPoint || !junctionId.trim() || !junctionName.trim()) return
    const result = await perform(() => dashboard.addIntersection({
      id: junctionId.trim(), name: junctionName.trim(), locationType,
      latitude: draftPoint.latitude, longitude: draftPoint.longitude,
    }), `Junction ${junctionId.trim()} added`)
    if (result) {
      setJunctionId('')
      setJunctionName('')
      setDraftPoint(null)
      setCaptureIntersection(false)
    }
  }

  const addRoad = async () => {
    const result = await perform(() => dashboard.addRoad({
      id: roadId.trim(), reverseRoadId: reverseRoadId.trim(),
      startIntersectionId: roadStart, endIntersectionId: roadEnd,
      speedLimit: Number(speedLimit), capacity: Number(capacity), bidirectional, roadClass,
    }), `Road ${roadId.trim()} added`)
    if (result) {
      const sources = result.geometrySources?.join(' / ')
      setNotice(`Road ${roadId.trim()} added · ${sources || 'geometry ready'}`)
      setRoadId('')
      setReverseRoadId('')
    }
  }

  const searchConnections = async (id = connectionId) => {
    if (!id) return
    const result = await perform(() => dashboard.connectedRoads(id), `Connections loaded for ${id}`)
    if (result) {
      setConnectionId(id)
      setConnections(result)
      onHighlightsChange([...result.incoming, ...result.outgoing].map((road) => road.id))
    }
  }

  const exportNetwork = async () => {
    const blob = await perform(dashboard.exportNetwork, 'Network exported')
    if (!blob) return
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'traffic-network.json'
    link.click()
    URL.revokeObjectURL(url)
  }

  return (
    <details className="rail-section network-editor">
      <summary><span>Network editor</span><b>{editable ? 'Draft streets' : 'Pause to edit'}</b></summary>
      <div className="section-body">
        {!editable ? <p className="editor-lock">Pause playback before changing the road graph.</p> : null}

        <div className="editor-group">
          <div className="editor-group-title"><strong>Add junction</strong><span>Click an exact map position</span></div>
          <div className="two-fields">
            <Field label="ID"><input value={junctionId} onChange={(event) => setJunctionId(event.target.value)} placeholder="I7" /></Field>
            <Field label="Type"><select value={locationType} onChange={(event) => setLocationType(event.target.value)}>
              {LOCATION_TYPES.map((type) => <option key={type}>{type}</option>)}</select></Field>
          </div>
          <Field label="Name"><input value={junctionName} onChange={(event) => setJunctionName(event.target.value)} placeholder="Landmark or junction name" /></Field>
          <div className="coordinate-readout">{draftPoint
            ? `${draftPoint.latitude.toFixed(6)}, ${draftPoint.longitude.toFixed(6)}`
            : 'No coordinate selected'}</div>
          <div className="action-row">
            <button className={captureIntersection ? 'active' : ''} disabled={!editable || busy}
              onClick={() => setCaptureIntersection(!captureIntersection)}>
              {captureIntersection ? 'Click map now' : 'Pick location'}
            </button>
            <button className="primary" disabled={!editable || busy || !draftPoint || !junctionId.trim() || !junctionName.trim()}
              onClick={addJunction}>Add junction</button>
          </div>
        </div>

        <div className="editor-group">
          <div className="editor-group-title"><strong>Add road</strong><span>Street geometry via OSRM</span></div>
          <div className="two-fields">
            <Field label="Road ID"><input value={roadId} onChange={(event) => {
              setRoadId(event.target.value)
              setReverseRoadId(`${event.target.value}-REV`)
            }} placeholder="R18" /></Field>
            <Field label="Reverse ID"><input value={reverseRoadId} disabled={!bidirectional}
              onChange={(event) => setReverseRoadId(event.target.value)} /></Field>
          </div>
          <div className="two-fields">
            <Field label="From"><select value={roadStart} onChange={(event) => setRoadStart(event.target.value)}>
              <option value="">Choose</option>{intersections.map((item) => <option key={item.id}>{item.id}</option>)}</select></Field>
            <Field label="To"><select value={roadEnd} onChange={(event) => setRoadEnd(event.target.value)}>
              <option value="">Choose</option>{intersections.map((item) => <option key={item.id}>{item.id}</option>)}</select></Field>
          </div>
          <div className="two-fields">
            <Field label="Speed km/h"><input type="number" min="1" value={speedLimit} onChange={(event) => setSpeedLimit(event.target.value)} /></Field>
            <Field label="Capacity"><input type="number" min="1" value={capacity} onChange={(event) => setCapacity(event.target.value)} /></Field>
          </div>
          <Field label="Street class"><select value={roadClass} onChange={(event) => setRoadClass(event.target.value)}>
            {ROAD_CLASSES.map((item) => <option key={item} value={item}>{item.replaceAll('_', ' ')}</option>)}
          </select></Field>
          <label className="check-field"><input type="checkbox" checked={bidirectional}
            onChange={(event) => setBidirectional(event.target.checked)} /><span>Create both directions</span></label>
          <button className="primary wide" disabled={!editable || busy || !roadId.trim() || !roadStart || !roadEnd
            || roadStart === roadEnd || bidirectional && !reverseRoadId.trim()} onClick={addRoad}>Route and add road</button>
        </div>

        <div className="editor-group">
          <div className="editor-group-title"><strong>Connected roads</strong><span>Incoming and outgoing</span></div>
          <div className="inline-search"><select value={connectionId} onChange={(event) => setConnectionId(event.target.value)}>
            <option value="">Choose junction</option>{intersections.map((item) => (
              <option key={item.id} value={item.id}>{item.id} · {item.name}</option>
            ))}</select>
            <button disabled={!connectionId || busy} onClick={() => searchConnections()}>Show</button></div>
          {(connections.incoming.length || connections.outgoing.length) ? <div className="connection-results">
            <span>IN {connections.incoming.map((road) => road.id).join(', ') || '—'}</span>
            <span>OUT {connections.outgoing.map((road) => road.id).join(', ') || '—'}</span>
          </div> : null}
        </div>

        {selectedRoad ? <div className="editor-group selected-editor">
          <div className="editor-group-title"><strong>Edit {selectedRoad.id}</strong><span>Condition and removal</span></div>
          <div className="two-fields">
            <Field label="Status"><select value={conditionStatus} onChange={(event) => setConditionStatus(event.target.value)}>
              {ROAD_STATUSES.map((item) => <option key={item}>{item}</option>)}</select></Field>
            <Field label="Congestion %"><input type="number" min="0" max="100" value={congestion}
              onChange={(event) => setCongestion(event.target.value)} /></Field>
          </div>
          <div className="action-row"><button className="primary" disabled={!editable || busy}
            onClick={() => perform(() => dashboard.updateRoadCondition(selectedRoad.id, {
              status: conditionStatus, congestionLevel: Number(congestion) / 100,
            }), `${selectedRoad.id} condition updated`)}>Save condition</button>
            <button className="danger-action" disabled={!editable || busy}
              onClick={async () => {
                const result = await perform(async () => {
                  await dashboard.removeRoad(selectedRoad.id)
                  return true
                }, `${selectedRoad.id} removed`)
                if (result !== null) onSelect(null)
              }}>Remove road</button></div>
        </div> : null}

        {selectedIntersection ? <div className="editor-group selected-editor">
          <div className="editor-group-title"><strong>{selectedIntersection.id}</strong><span>{selectedIntersection.name}</span></div>
          <div className="action-row"><button onClick={() => searchConnections(selectedIntersection.id)}>Show connections</button>
            <button className="danger-action" disabled={!editable || busy}
              onClick={async () => {
                const result = await perform(async () => {
                  await dashboard.removeIntersection(selectedIntersection.id)
                  return true
                }, `${selectedIntersection.id} removed`)
                if (result !== null) onSelect(null)
              }}>Remove junction</button></div>
        </div> : null}

        <button className="wide" disabled={busy} onClick={exportNetwork}>Export network JSON</button>
        {notice ? <p className="editor-notice" role="status">{notice}</p> : null}
      </div>
    </details>
  )
}

export default memo(NetworkEditor)
