import { useState, useEffect, useRef } from 'react'

export default function MapViewport({ roadNetworkHook }) {
  const {
    intersections,
    roads,
    loading,
    error,
    loadIntersections,
    loadRoads,
    generateGridMap,
  } = roadNetworkHook

  const canvasRef = useRef(null)
  const [scale, setScale] = useState(1)
  const [panX, setPanX] = useState(0)
  const [panY, setPanY] = useState(0)
  const [selectedIntersection, setSelectedIntersection] = useState(null)
  const [showGrid, setShowGrid] = useState(false)

  // Generate grid map
  const handleGenerateGridMap = async () => {
    try {
      await generateGridMap(5, 5)
      await loadIntersections()
      await loadRoads()
    } catch (err) {
      console.error('Failed to generate map:', err)
    }
  }

  // Draw the map
  useEffect(() => {
    const canvas = canvasRef.current
    if (!canvas || !intersections || !roads) return

    const ctx = canvas.getContext('2d')
    const width = canvas.width
    const height = canvas.height

    // Clear canvas
    ctx.fillStyle = '#f0f0f0'
    ctx.fillRect(0, 0, width, height)

    // Draw grid
    if (showGrid) {
      ctx.strokeStyle = '#ddd'
      ctx.lineWidth = 0.5
      for (let i = 0; i < width; i += 50) {
        ctx.beginPath()
        ctx.moveTo(i, 0)
        ctx.lineTo(i, height)
        ctx.stroke()
      }
      for (let i = 0; i < height; i += 50) {
        ctx.beginPath()
        ctx.moveTo(0, i)
        ctx.lineTo(width, i)
        ctx.stroke()
      }
    }

    // Draw roads
    ctx.strokeStyle = '#333'
    ctx.lineWidth = 2
    roads.forEach((road) => {
      const startIntersection = intersections.find((i) => i.id === road.startIntersectionId)
      const endIntersection = intersections.find((i) => i.id === road.endIntersectionId)

      if (startIntersection && endIntersection) {
        const x1 = (startIntersection.x || 0) * scale + panX
        const y1 = (startIntersection.y || 0) * scale + panY
        const x2 = (endIntersection.x || 0) * scale + panX
        const y2 = (endIntersection.y || 0) * scale + panY

        // Draw road
        ctx.beginPath()
        ctx.moveTo(x1, y1)
        ctx.lineTo(x2, y2)

        ctx.strokeStyle = road.getStatusColor()
        ctx.stroke()
      }
    })

    // Draw intersections
    intersections.forEach((intersection) => {
      const x = (intersection.x || 0) * scale + panX
      const y = (intersection.y || 0) * scale + panY

      // Draw circle
      ctx.fillStyle =
        selectedIntersection?.id === intersection.id ? '#4169E1' : '#FFD700'
      ctx.beginPath()
      ctx.arc(x, y, 6, 0, 2 * Math.PI)
      ctx.fill()

      // Draw border
      ctx.strokeStyle = '#333'
      ctx.lineWidth = 1.5
      ctx.stroke()

      // Draw label
      ctx.fillStyle = '#000'
      ctx.font = '10px Arial'
      ctx.textAlign = 'center'
      ctx.fillText(intersection.id, x, y + 15)
    })
  }, [intersections, roads, scale, panX, panY, selectedIntersection, showGrid])

  // Handle canvas clicks
  const handleCanvasClick = (e) => {
    const canvas = canvasRef.current
    const rect = canvas.getBoundingClientRect()
    const clickX = (e.clientX - rect.left - panX) / scale
    const clickY = (e.clientY - rect.top - panY) / scale

    // Find clicked intersection
    intersections.forEach((intersection) => {
      const ix = intersection.x || 0
      const iy = intersection.y || 0
      const distance = Math.sqrt((clickX - ix) ** 2 + (clickY - iy) ** 2)

      if (distance < 10) {
        setSelectedIntersection(intersection)
      }
    })
  }

  // Handle zoom
  const handleZoom = (e) => {
    e.preventDefault()
    const delta = e.deltaY > 0 ? 0.9 : 1.1
    setScale((s) => Math.max(0.5, Math.min(5, s * delta)))
  }

  // Handle pan
  const panState = useRef(null)

  const handleMouseMove = (e) => {
    if (!panState.current) return
    const dx = e.clientX - panState.current.x
    const dy = e.clientY - panState.current.y
    setPanX((px) => px + dx)
    setPanY((py) => py + dy)
    panState.current = { x: e.clientX, y: e.clientY }
  }

  const handleMouseDown = (e) => {
    if (e.button === 2) {
      // Right click to pan
      panState.current = { x: e.clientX, y: e.clientY }
    } else {
      handleCanvasClick(e)
    }
  }

  const handleMouseUp = () => {
    panState.current = null
  }

  return (
    <div className="map-viewport">
      <h2>Road Network Map</h2>

      {/* Controls */}
      <div className="map-controls">
        <button onClick={() => setScale(1)} className="btn-secondary">
          Reset Zoom
        </button>
        <button onClick={() => { setPanX(0); setPanY(0); }} className="btn-secondary">
          Reset Pan
        </button>
        <button onClick={() => setShowGrid(!showGrid)} className="btn-secondary">
          {showGrid ? 'Hide Grid' : 'Show Grid'}
        </button>
        <button onClick={handleGenerateGridMap} disabled={loading} className="btn-primary">
          {loading ? 'Generating...' : 'Generate 5x5 Grid'}
        </button>
        <div className="zoom-info">
          Zoom: {scale.toFixed(2)}x | Pan: ({panX.toFixed(0)}, {panY.toFixed(0)})
        </div>
      </div>

      {/* Canvas */}
      <canvas
        ref={canvasRef}
        width={800}
        height={600}
        onContextMenu={(e) => e.preventDefault()}
        onWheel={handleZoom}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        style={{
          border: '1px solid #ccc',
          cursor: 'grab',
          display: 'block',
          margin: '10px 0',
        }}
      />

      {/* Info */}
      <div className="map-info">
        <p>
          Intersections: <strong>{intersections?.length || 0}</strong> | Roads: <strong>{roads?.length || 0}</strong>
        </p>
        {selectedIntersection && (
          <div className="selected-info">
            <h4>Selected Intersection: {selectedIntersection.id}</h4>
            <p>Location Type: {selectedIntersection.locationType || 'NORMAL'}</p>
            <p>Position: ({selectedIntersection.x || 0}, {selectedIntersection.y || 0})</p>
          </div>
        )}
      </div>

      {/* Error Display */}
      {error && <div className="error-message">{error}</div>}
    </div>
  )
}
