import { useEffect, useRef, useState } from 'react'

const SEEK_DEBOUNCE_MILLIS = 120

function formatDuration(seconds) {
  const value = Number(seconds || 0)
  if (value < 60) return `${value.toFixed(0)} s`
  return `${(value / 60).toFixed(1)} min`
}

function nearestFrameIndex(recordedSeconds, elapsedSeconds) {
  if (!recordedSeconds.length) return 0
  let nearestIndex = 0
  let nearestDistance = Number.POSITIVE_INFINITY
  recordedSeconds.forEach((seconds, index) => {
    const distance = Math.abs(Number(seconds) - Number(elapsedSeconds || 0))
    if (distance < nearestDistance) {
      nearestDistance = distance
      nearestIndex = index
    }
  })
  return nearestIndex
}

export default function TimelineScrubber({ timeline, frame, viewingHistory, loading, onSeek, onLive,
  roadCount, intersectionCount, incidentCount }) {
  const recordedSeconds = timeline.recordedSeconds || []
  const selectedIndex = viewingHistory
    ? nearestFrameIndex(recordedSeconds, frame.elapsedSeconds)
    : Math.max(0, recordedSeconds.length - 1)
  const [draftIndex, setDraftIndex] = useState(selectedIndex)
  const scrubbingRef = useRef(false)
  const seekTimerRef = useRef(null)

  useEffect(() => {
    if (!scrubbingRef.current) setDraftIndex(selectedIndex)
  }, [selectedIndex])

  useEffect(() => () => {
    if (seekTimerRef.current != null) window.clearTimeout(seekTimerRef.current)
  }, [])

  const draftSeconds = recordedSeconds[draftIndex] ?? frame.elapsedSeconds ?? 0
  const changeFrame = (event) => {
    const nextIndex = Number(event.currentTarget.value)
    setDraftIndex(nextIndex)
    scrubbingRef.current = true
    if (seekTimerRef.current != null) window.clearTimeout(seekTimerRef.current)
    seekTimerRef.current = window.setTimeout(() => {
      seekTimerRef.current = null
      const elapsedSeconds = recordedSeconds[nextIndex]
      const shouldSeek = elapsedSeconds != null
        && (!viewingHistory || Math.abs(elapsedSeconds - Number(frame.elapsedSeconds || 0)) > 1e-9)
      Promise.resolve(shouldSeek ? onSeek(elapsedSeconds) : null)
        .finally(() => { scrubbingRef.current = false })
    }, SEEK_DEBOUNCE_MILLIS)
  }

  return (
    <footer className={`tick-rail ${viewingHistory ? 'history' : 'live'}`}>
      <div><span>{viewingHistory ? 'History frame' : 'Simulation timeline'}</span>
        <strong>{formatDuration(draftSeconds)}</strong></div>
      <input className="timeline-scrubber" type="range" min="0"
        max={Math.max(0, recordedSeconds.length - 1)} step="1" value={draftIndex}
        aria-label="Recorded simulation frame" disabled={loading || recordedSeconds.length < 2}
        onChange={changeFrame} />
      <button className={`timeline-live-button ${viewingHistory ? '' : 'active'}`}
        disabled={!viewingHistory || loading} onClick={onLive}>
        {loading ? 'Loading…' : 'Live'}
      </button>
      <span>Frame {frame.frameId ?? '—'}</span>
      <span>{roadCount} roads · {intersectionCount} junctions · {incidentCount} incidents</span>
    </footer>
  )
}
