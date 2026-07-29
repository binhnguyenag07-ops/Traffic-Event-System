import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import TrafficMap from './components/TrafficMap'
import NetworkEditor from './components/NetworkEditor'
import ExperimentPanel from './components/ExperimentPanel'
import TimelineScrubber from './components/TimelineScrubber'
import DemandSchedulePanel from './components/DemandSchedulePanel'
import { useTrafficDashboard } from './hooks/useTrafficDashboard'
import { DEFAULT_PHYSICAL_ZOOM } from './mapViewport'
import { isVehicleVisibleOnMap } from './vehicleVisibility'
import { inspectorEntries } from './inspector'
import { buildingLabel, intersectionLabel, roadLabel } from './displayLabels'
import { formatTimeOfDay, timeOfDaySeconds } from './simulationTime'

// PixiJS is only needed in the lane-level view. Keeping it out of the initial
// dashboard bundle makes the normal Leaflet view interactive sooner and avoids
// constructing the WebGL stack until the user actually asks for it.
const PhysicalScene = lazy(() => import('./physical/PhysicalScene'))

const PREFERENCES = [
  ['CONGESTION_AWARE', 'Traffic aware'],
  ['SHORTEST_TIME', 'Fastest'],
  ['SHORTEST_DISTANCE', 'Shortest'],
  ['MINIMUM_HOPS', 'Fewest roads'],
]

const EVENT_TYPES = [
  ['CONGESTION', 'Congestion'],
  ['ACCIDENT', 'Accident'],
  ['BREAKDOWN', 'Vehicle breakdown'],
  ['ROAD_CLOSURE', 'Road closure'],
]

const SIMULATION_PACES = [
  { id: 'DETAIL', label: 'Lane detail · 2 sim s/s', step: 0.5, interval: 250 },
  { id: 'BALANCED', label: 'Balanced · 10 sim s/s', step: 2.5, interval: 250 },
  { id: 'DAY', label: '24 h overview · 120 sim s/s', step: 30, interval: 250 },
]
const PHYSICAL_DETAIL_PACE = SIMULATION_PACES[0]

function Metric({ label, value, tone = '' }) {
  return (
    <div className={`metric ${tone}`}>
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Field({ label, children }) {
  return <label className="field"><span>{label}</span>{children}</label>
}

function formatDuration(seconds) {
  const value = Number(seconds || 0)
  if (value < 60) return `${value.toFixed(0)} s`
  return `${(value / 60).toFixed(1)} min`
}

function Inspector({ selected }) {
  if (!selected) {
    return <p className="empty-copy">Select a road, junction, vehicle, or event on the map to inspect it.</p>
  }
  const entries = inspectorEntries(selected)
  return (
    <div className="inspector-grid">
      <span className="object-kind">{selected.kind}</span>
      {entries.map(({ key, label, value }) => (
        <div key={key}><span>{label}</span><strong>{String(value)}</strong></div>
      ))}
    </div>
  )
}

// `?physical=<lat>,<lon>[,<radiusMetres>]` opens the lane view straight away.
// It makes a junction shareable as a link, and it is how the view is opened
// for a demo without hunting for the right zoom first.
function physicalTargetFromUrl() {
  if (typeof window === 'undefined') return null
  const raw = new URLSearchParams(window.location.search).get('physical')
  if (!raw) return null
  const [latitude, longitude, radiusMetres, zoom] = raw.split(',').map(Number)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
  return {
    latitude,
    longitude,
    radiusMetres: Number.isFinite(radiusMetres) ? radiusMetres : 220,
    zoom: Number.isFinite(zoom) ? zoom : DEFAULT_PHYSICAL_ZOOM,
  }
}

// `?lab=<scenarioId>` opens one physical-view test case: it activates that
// scenario, drops straight into the lane view at the case's own centre, and
// leaves the basemap out.
//
// No basemap because the test maps are metre-exact fiction laid over real
// coordinates. An OpenStreetMap backdrop under them would invite comparing a
// drawn lane against a street that has nothing to do with it, and the whole
// value of these cases is that the correct picture is known in advance.
function labIdFromUrl() {
  if (typeof window === 'undefined') return null
  const raw = new URLSearchParams(window.location.search).get('lab')
  return raw && raw.trim() ? raw.trim() : null
}

/**
 * What this case is supposed to show, and a way to step to the next one.
 *
 * The sentence is the point. A case nobody can grade is just a picture, so each
 * one states what a correct render looks like before you go looking at it.
 */
function LabBanner({ scenario, cases, onSelectCase, onLeave, busy }) {
  if (!scenario) return null
  const index = cases.findIndex((entry) => entry.id === scenario.id)
  const go = (offset) => {
    const next = cases[(index + offset + cases.length) % cases.length]
    if (next) onSelectCase(next.id)
  }
  return (
    <div className="lab-banner" role="status">
      <div className="lab-heading">
        <span className="eyebrow">
          Physical view test {index + 1} of {cases.length}
        </span>
        <h2>{scenario.name}</h2>
      </div>
      <p>{scenario.lab?.expect || scenario.description}</p>
      <div className="lab-nav">
        {/* Stepping between cases activates the next one in place. It used to
            assign window.location.search, reloading the whole page to change
            one scenario — which threw away the loaded network, the Pixi
            context and the run, and read as the app hanging. */}
        <button onClick={() => go(-1)} disabled={busy || cases.length < 2}>← Previous</button>
        <button onClick={() => go(1)} disabled={busy || cases.length < 2}>Next →</button>
        {/* Leaving means activating a real scenario, not clearing the URL. The
            lab hides the basemap because a test map is metre-exact fiction laid
            over real coordinates; while the server still has one loaded there
            is nothing honest to go back to. Clearing the URL alone reloaded
            straight back into the lab, because lab mode is inferred from
            whichever scenario the server holds. */}
        <button onClick={onLeave} disabled={busy}>Leave the lab</button>
      </div>
    </div>
  )
}

export default function App() {
  const dashboard = useTrafficDashboard()
  const [labId, setLabId] = useState(labIdFromUrl)
  const [initialPhysical] = useState(physicalTargetFromUrl)
  const [physicalOpen, setPhysicalOpen] = useState(Boolean(initialPhysical))
  const [mapViewport, setMapViewport] = useState(() => initialPhysical
    ? {
        latitude: initialPhysical.latitude,
        longitude: initialPhysical.longitude,
        zoom: initialPhysical.zoom,
        radiusMetres: initialPhysical.radiusMetres,
      }
    : null)
  const {
    intersections, roads, networkRoads, viewRoads, buildings, scenarios, activeScenario, snapshot, viewFrame,
    timeline, viewingHistory, historyLoading, statistics, algorithms,
    route, comparison, selected, loading, error, tickDelay, streamConnected, importSummary,
    simulationClock, demandSchedule, setSimulationPace,
  } = dashboard
  const [routeStart, setRouteStart] = useState('')
  const [routeEnd, setRouteEnd] = useState('')
  const [preference, setPreference] = useState('CONGESTION_AWARE')
  const [algorithm, setAlgorithm] = useState('')
  const [vehicleType, setVehicleType] = useState('CAR')
  const [vehicleStart, setVehicleStart] = useState('')
  const [vehicleEnd, setVehicleEnd] = useState('')
  const [lastRandomSpawn, setLastRandomSpawn] = useState(null)
  const [spawningRandomVehicle, setSpawningRandomVehicle] = useState(false)
  const [eventType, setEventType] = useState('CONGESTION')
  const [eventRoad, setEventRoad] = useState('')
  const [eventDuration, setEventDuration] = useState(30)
  const [eventPositionPercent, setEventPositionPercent] = useState(50)
  const [eventLaneIndex, setEventLaneIndex] = useState('')
  const [eventVehicleId, setEventVehicleId] = useState('')
  const [captureIntersection, setCaptureIntersection] = useState(false)
  const [draftPoint, setDraftPoint] = useState(null)
  const [highlightedRoadIds, setHighlightedRoadIds] = useState([])
  const mapFileRef = useRef(null)
  const labStarted = useRef(false)
  const prePhysicalPaceRef = useRef(null)

  const labCases = useMemo(() => scenarios.filter((entry) => entry.lab), [scenarios])
  const labScenario = labId ? scenarios.find((entry) => entry.id === labId) : null
  // A lab case picked from the dropdown counts too, not only one named in the
  // URL. Its map is synthetic, so the basemap underneath would show a street
  // that has nothing to do with what is drawn on top of it.
  const activeLab = labScenario
    || scenarios.find((entry) => entry.id === activeScenario && entry.lab)
    || null
  const labMode = Boolean(activeLab)

  // Activate the case named in the URL once. Guarded by a ref rather than by
  // comparing against activeScenario: activation resets the run, so re-firing
  // would restart the case every time a frame arrived.
  useEffect(() => {
    if (!labScenario || labStarted.current) return
    labStarted.current = true
    dashboard.activateScenario(labScenario.id).catch(() => {})
  }, [dashboard, labScenario])

  // Where the live case wants the camera, or null if it does not say. A case
  // with no usable centre gets no viewport, and so never opens the lane view on
  // nothing: in the lab the basemap is hidden too, so a scene with nowhere to
  // point is simply a blank stage.
  const labViewport = useMemo(() => {
    const [latitude, longitude] = activeLab?.lab?.centre || []
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return null
    return {
      latitude,
      longitude,
      zoom: DEFAULT_PHYSICAL_ZOOM,
      radiusMetres: Number(activeLab.lab.radiusMetres) || 220,
    }
  }, [activeLab])

  // Whichever case is live — named in the URL or picked from the list — drops
  // into the lane view at its own centre. This has to re-fire when the case
  // changes: it used to run only for a case that was not the one in the URL, so
  // stepping between cases left every later one framed on the first one's
  // centre and radius.
  useEffect(() => {
    if (!labViewport) return
    setMapViewport(labViewport)
    setPhysicalOpen(true)
  }, [labViewport])

  // Large overview ticks are useful for crossing a full simulated day, but one
  // live frame can span several junctions. The lane-level view switches back to
  // the sub-second authoritative cadence so its interpolation always has the
  // road/connector endpoints needed to keep vehicles on the tarmac.
  useEffect(() => {
    if (!physicalOpen || simulationClock.simulationSecondsPerTick <= 1) return
    if (!prePhysicalPaceRef.current) {
      prePhysicalPaceRef.current = {
        step: simulationClock.simulationSecondsPerTick,
        interval: Number(tickDelay),
      }
    }
    setSimulationPace(
      PHYSICAL_DETAIL_PACE.step,
      PHYSICAL_DETAIL_PACE.interval,
    ).catch(() => {})
  }, [
    physicalOpen,
    setSimulationPace,
    simulationClock.simulationSecondsPerTick,
    tickDelay,
  ])

  // Every in-session scenario change goes through here, from the picker and
  // from the lab's own Previous/Next.
  //
  // The camera is aimed in the same act as the switch, deliberately. The lane
  // view reads its viewport once when it mounts and is rebuilt whenever the
  // scenario changes, so if the aim arrived in a later render than the switch
  // it would rebuild against the outgoing case's centre and go on polling
  // there — the new case selected, named in the banner, and nowhere on screen.
  // Both updates in one event handler means one commit, and one rebuild with
  // the right target.
  //
  // The URL is cleared at the same time: a ?lab= parameter outranks the active
  // scenario when deciding which case is live, so leaving it set pinned the
  // banner to the case arrived on however many times you stepped past it.
  const chooseScenario = useCallback((id) => {
    const target = scenarios.find((entry) => entry.id === id)
    const [latitude, longitude] = target?.lab?.centre || []
    if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
      setMapViewport({
        latitude,
        longitude,
        zoom: DEFAULT_PHYSICAL_ZOOM,
        radiusMetres: Number(target.lab.radiusMetres) || 220,
      })
      setPhysicalOpen(true)
    }
    setLabId(null)
    labStarted.current = true
    window.history.replaceState(null, '', window.location.pathname)
    dashboard.activateScenario(id).catch(() => {})
  }, [dashboard, scenarios])

  // Leaving the lab has to put a real scenario back on the server. Lab mode is
  // inferred from whichever scenario is loaded, so merely clearing the URL and
  // reloading landed straight back in the lab it was trying to leave — with the
  // basemap still suppressed and the lane view reopening itself.
  const leaveLab = useCallback(() => {
    const real = scenarios.find((entry) => !entry.lab)
    setLabId(null)
    labStarted.current = true
    window.history.replaceState(null, '', window.location.pathname)
    prePhysicalPaceRef.current = null
    setPhysicalOpen(false)
    setMapViewport(null)
    if (real) dashboard.activateScenario(real.id).catch(() => {})
  }, [dashboard, scenarios])

  const closePhysical = useCallback((viewport) => {
    if (viewport) setMapViewport(viewport)
    setPhysicalOpen(false)
    const previousPace = prePhysicalPaceRef.current
    prePhysicalPaceRef.current = null
    if (previousPace) {
      setSimulationPace(previousPace.step, previousPace.interval).catch(() => {})
    }
  }, [setSimulationPace])

  const traffic = statistics.traffic || {}
  const activeEvents = snapshot.trafficEvents?.filter((event) => event.status === 'ACTIVE') || []
  const viewActiveEvents = viewFrame.trafficEvents?.filter((event) => event.status === 'ACTIVE') || []
  // Demand-backed scenarios may legitimately have an empty fleet before the
  // first scheduled departure. The server, not the current sprite count,
  // decides whether READY can transition to RUNNING.
  const canStart = snapshot.status === 'READY'
  const canPause = snapshot.status === 'RUNNING'
  const canResume = snapshot.status === 'PAUSED'
  const viewRoadStates = viewFrame.roadStates || []
  const visibleVehicles = useMemo(
    () => (viewFrame.vehicles || []).filter(isVehicleVisibleOnMap),
    [viewFrame.vehicles],
  )
  const pressure = viewRoadStates.length
    ? Math.round(viewRoadStates.reduce((sum, road) => sum + Number(road.congestionLevel || 0), 0)
      / viewRoadStates.length * 100)
    : Math.round(Number(traffic.averageCongestionLevel || 0) * 100)
  const scenarioName = scenarios.find((scenario) => scenario.id === activeScenario)?.name || 'District 1 Live'
  const intersectionById = useMemo(
    () => new Map(intersections.map((intersection) => [intersection.id, intersection])),
    [intersections],
  )
  const buildingById = useMemo(
    () => new Map(buildings.map((building) => [building.id, building])),
    [buildings],
  )
  const intersectionOptions = useMemo(
    () => [...intersections].sort((left, right) => (
      intersectionLabel(left).localeCompare(intersectionLabel(right), 'vi')
    )),
    [intersections],
  )
  const buildingOptions = useMemo(
    () => [...buildings].sort((left, right) => (
      buildingLabel(left, intersectionById).localeCompare(
        buildingLabel(right, intersectionById),
        'vi',
      )
    )),
    [buildings, intersectionById],
  )
  const roadOptions = useMemo(
    () => [...networkRoads].sort((left, right) => (
      roadLabel(left, intersectionById).localeCompare(roadLabel(right, intersectionById), 'vi')
    )),
    [intersectionById, networkRoads],
  )
  const intersectionOptionNodes = useMemo(
    () => intersectionOptions.map((intersection) => (
      <option key={intersection.id} value={intersection.id}>{intersectionLabel(intersection)}</option>
    )),
    [intersectionOptions],
  )
  const buildingOptionNodes = useMemo(
    () => buildingOptions.map((building) => (
      <option key={building.id} value={building.id}>
        {buildingLabel(building, intersectionById)}
      </option>
    )),
    [buildingOptions, intersectionById],
  )
  const roadOptionNodes = useMemo(
    () => roadOptions.map((road) => (
      <option key={road.id} value={road.id}>{roadLabel(road, intersectionById)}</option>
    )),
    [intersectionById, roadOptions],
  )
  const clock = formatTimeOfDay(timeOfDaySeconds(
    viewFrame,
    simulationClock.startTimeOfDaySeconds,
  ))
  const selectedPace = SIMULATION_PACES.find((pace) => (
    Math.abs(pace.step - simulationClock.simulationSecondsPerTick) < 1e-9
    && pace.interval === Number(tickDelay)
  ))?.id || 'CUSTOM'

  const algorithmOptions = useMemo(
    () => algorithms.map((item) => item.algorithmName || item.name).filter(Boolean),
    [algorithms],
  )

  const safely = (action) => action().catch(() => {})
  const pickIntersection = (id) => {
    if (!routeStart || routeEnd) {
      setRouteStart(id)
      setRouteEnd('')
    } else {
      setRouteEnd(id)
    }
  }

  const findRoute = () => {
    if (!routeStart || !routeEnd) return
    safely(() => dashboard.findRoute(routeStart, routeEnd, preference, algorithm || undefined))
  }

  const addVehicle = () => {
    if (!vehicleStart || !vehicleEnd) return
    safely(async () => {
      await dashboard.createVehicleBetweenBuildings(vehicleType, vehicleStart, vehicleEnd)
      setVehicleStart('')
      setVehicleEnd('')
    })
  }

  const spawnRandomVehicle = () => {
    if (spawningRandomVehicle) return
    setSpawningRandomVehicle(true)
    safely(async () => {
      try {
        const spawned = await dashboard.spawnRandomVehicle()
        setLastRandomSpawn(spawned)
        dashboard.setSelected({ kind: 'vehicle', data: spawned.vehicle })
      } finally {
        setSpawningRandomVehicle(false)
      }
    })
  }

  const addEvent = () => {
    if (!eventRoad) return
    if (eventType === 'BREAKDOWN' && !eventVehicleId) return
    const isPositioned = eventType === 'ACCIDENT' || eventType === 'BREAKDOWN'
    const value = eventType === 'ROAD_CLOSURE' || eventType === 'BREAKDOWN' ? undefined : 0.55
    const elapsedSeconds = Number(snapshot.elapsedSeconds ?? snapshot.tick ?? 0)
    safely(async () => {
      await dashboard.createEvent({
        type: eventType,
        affectedRoadId: eventRoad,
        startTimeSeconds: elapsedSeconds,
        durationSeconds: Number(eventDuration),
        value,
        affectedVehicleId: eventType === 'BREAKDOWN' ? eventVehicleId : undefined,
        positionAlongRoad: isPositioned ? Number(eventPositionPercent) / 100 : undefined,
        blockedLaneIndexes: isPositioned && eventLaneIndex !== '' ? [Number(eventLaneIndex)] : undefined,
      })
      setEventRoad('')
      setEventVehicleId('')
    })
  }

  return (
    <main className="control-room">
      <header className="topbar">
        <div className="brand-block">
          <span className="brand-mark" aria-hidden="true"><i /><i /><i /></span>
          <div><span className="eyebrow">Urban traffic simulator</span><h1>Flow control</h1></div>
        </div>
        <div className="live-strip" role="status" aria-live="polite">
          <span className={`connection-dot ${streamConnected ? 'connected' : ''}`} />
          <span>{viewingHistory ? 'Recorded frame' : streamConnected ? 'Live stream' : 'Reconnecting'}</span>
          <strong>{viewingHistory ? 'HISTORY' : snapshot.status}</strong>
          <b className="simulation-clock" aria-label={`Simulation time ${clock}`}>{clock}</b>
          <b>T+{formatDuration(viewFrame.elapsedSeconds)}</b>
        </div>
        <div className="scenario-switcher">
          <label><span>Scenario</span>
            {/* Test cases are grouped apart from the real runs. Listed together
                they read as alternative cities, and picking a fifteen-metre
                rendering fixture looks like the map broke rather than like a
                deliberate choice. */}
            <select value={activeScenario} disabled={loading}
              onChange={(event) => chooseScenario(event.target.value)}>
              {scenarios.filter((scenario) => !scenario.lab).map((scenario) => (
                <option key={scenario.id} value={scenario.id}>{scenario.name}</option>
              ))}
              {labCases.length > 0 && (
                <optgroup label="Physical view test cases">
                  {labCases.map((scenario) => (
                    <option key={scenario.id} value={scenario.id}>{scenario.name}</option>
                  ))}
                </optgroup>
              )}
            </select>
          </label>
          <input ref={mapFileRef} className="visually-hidden" type="file" accept="application/json,.json"
            onChange={(event) => {
              const file = event.target.files?.[0]
              if (file) safely(() => dashboard.importMap(file))
              event.target.value = ''
            }} />
          <button className="import-map-button" disabled={loading} onClick={() => mapFileRef.current?.click()}>
            {loading ? 'Working…' : 'Import JSON'}
          </button>
        </div>
      </header>

      {error || importSummary ? <div className="status-stack">
        {error ? <div className="global-error" role="alert"><strong>Connection issue</strong><span>{error}</span>
          <button onClick={dashboard.refreshAll}>Retry</button></div> : null}
        {importSummary ? <div className="import-notice" role="status">
          <strong>{importSummary.filename}</strong>
          <span>{importSummary.intersectionCount} junctions · {importSummary.roadCount} roads</span>
          <span>{importSummary.geometrySources?.OSRM || 0} routed · {importSummary.geometrySources?.STRAIGHT || 0} fallback</span>
        </div> : null}
      </div> : null}

      <LabBanner
        scenario={activeLab}
        cases={labCases}
        onSelectCase={chooseScenario}
        onLeave={leaveLab}
        busy={loading}
      />

      <section className="workspace">
        <div className="map-stage">
          {!labMode && <TrafficMap
            intersections={intersections}
            roads={viewRoads}
            vehicles={visibleVehicles}
            events={viewFrame.trafficEvents || []}
            route={route}
            selected={selected}
            onSelect={dashboard.setSelected}
            onPickIntersection={pickIntersection}
            captureIntersection={captureIntersection}
            draftIntersection={draftPoint}
            highlightedRoadIds={highlightedRoadIds}
            viewport={mapViewport}
            onViewportChange={setMapViewport}
            fitNetwork={!initialPhysical}
            onMapCoordinate={(point) => {
              setDraftPoint(point)
              setCaptureIntersection(false)
            }}
            onOpenPhysical={(viewport) => {
              setMapViewport(viewport)
              setPhysicalOpen(true)
            }}
          />}
          {physicalOpen && mapViewport && (
            <Suspense fallback={(
              <div className="physical-overlay" role="dialog" aria-label="Physical lane view">
                <div className="physical-toolbar">
                  <strong>Physical view</strong>
                  <span className="physical-note">Loading renderer…</span>
                </div>
              </div>
            )}>
              <PhysicalScene
                // A new scenario is a new world, so the scene is rebuilt rather
                // than re-aimed. Its camera, origin and radius are read once at
                // mount and never from the prop again, so without this it went on
                // polling the previous case's centre and radius after a switch —
                // drawing the old patch of map, or nothing at all, while the
                // banner and the picker both showed the new case.
                key={activeScenario}
                viewport={mapViewport}
                onViewportChange={setMapViewport}
                onClose={labMode
                  // Closing in the lab would leave an empty stage, since the
                  // basemap is not rendered here. Leave the lab instead.
                  ? () => { window.location.search = '' }
                  : closePhysical}
              />
            </Suspense>
          )}
          <div className="map-metrics" aria-label={viewingHistory ? 'Historical traffic summary' : 'Live traffic summary'}>
            <Metric label="Fleet" value={visibleVehicles.length} />
            <Metric label={viewingHistory ? 'Frame time' : 'Average trip'}
              value={viewingHistory ? formatDuration(viewFrame.elapsedSeconds) : formatDuration(traffic.averageTravelTimeSeconds)} />
            <Metric label="Network pressure" value={`${pressure}%`} tone={pressure > 65 ? 'danger' : ''} />
            <Metric label="Active incidents" value={viewActiveEvents.length} tone={viewActiveEvents.length ? 'warning' : ''} />
            <Metric label={viewingHistory ? 'Frame ID' : 'Arrived'}
              value={viewingHistory ? viewFrame.frameId : traffic.arrivedVehicles || 0} />
          </div>
        </div>

        <aside className="command-rail">
          <section className="rail-section simulation-section">
            <div className="section-heading"><div><span className="eyebrow">Playback</span><h2>{scenarioName}</h2></div>
              <span className={`status-chip ${snapshot.status.toLowerCase()}`}>{snapshot.status}</span></div>
            <div className="transport-controls">
              <button className="primary" disabled={!canStart || loading} onClick={() => safely(dashboard.start)}>Start</button>
              <button disabled={!canPause || loading} onClick={() => safely(dashboard.pause)}>Pause</button>
              <button disabled={!canResume || loading} onClick={() => safely(dashboard.resume)}>Resume</button>
              <button disabled={snapshot.status === 'RUNNING' || loading} onClick={() => safely(dashboard.advanceTick)}>Step</button>
              <button className="quiet" disabled={loading} onClick={() => safely(dashboard.reset)}>Reset</button>
            </div>
            <div className="setup-row">
              <button onClick={() => safely(() => dashboard.activateScenario(activeScenario))}
                disabled={loading || snapshot.status === 'RUNNING' || activeScenario === 'imported'}>
                Reload scenario
              </button>
              <Field label="Simulation pace">
                <select value={selectedPace} onChange={(event) => {
                  const pace = SIMULATION_PACES.find((item) => item.id === event.target.value)
                  if (pace) safely(() => setSimulationPace(pace.step, pace.interval))
                }}>
                  {selectedPace === 'CUSTOM' ? <option value="CUSTOM">Current custom pace</option> : null}
                  {SIMULATION_PACES.map((pace) => (
                    <option key={pace.id} value={pace.id}>{pace.label}</option>
                  ))}
                </select>
              </Field>
            </div>
          </section>

          <DemandSchedulePanel
            schedule={demandSchedule}
            currentTimeOfDaySeconds={timeOfDaySeconds(
              viewFrame,
              simulationClock.startTimeOfDaySeconds,
            )}
          />

          <details className="rail-section" open>
            <summary><span>Route lab</span><b>{route ? route.algorithm : 'Plan a trip'}</b></summary>
            <div className="section-body">
              <div className="two-fields">
                <Field label="From"><select value={routeStart} onChange={(e) => setRouteStart(e.target.value)}><option value="">Choose junction</option>{intersectionOptionNodes}</select></Field>
                <Field label="To"><select value={routeEnd} onChange={(e) => setRouteEnd(e.target.value)}><option value="">Choose junction</option>{intersectionOptionNodes}</select></Field>
              </div>
              <div className="two-fields">
                <Field label="Preference"><select value={preference} onChange={(e) => setPreference(e.target.value)}>{PREFERENCES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
                <Field label="Algorithm"><select value={algorithm} onChange={(e) => setAlgorithm(e.target.value)}><option value="">Automatic</option>{algorithmOptions.map((name) => <option key={name}>{name}</option>)}</select></Field>
              </div>
              <div className="action-row"><button className="primary" onClick={findRoute} disabled={!routeStart || !routeEnd}>Show route</button>
                <button onClick={() => safely(() => dashboard.compareRoutes(routeStart, routeEnd, preference))} disabled={!routeStart || !routeEnd}>Compare all</button></div>
              {route ? <div className="route-readout"><Metric label="Distance" value={`${route.totalDistance?.toFixed(1)} km`} /><Metric label="Travel time" value={`${(route.estimatedTravelTime * 60).toFixed(1)} min`} /><Metric label="Visited" value={route.visitedNodes} /></div> : null}
              {comparison.length ? <div className="comparison-list">{comparison.map((item) => <div key={item.algorithm}><strong>{item.algorithm}</strong><span>{item.success ? `${item.result.computationTimeMillis} ms · ${item.result.visitedNodes} nodes` : item.error}</span></div>)}</div> : null}
            </div>
          </details>

          <details className="rail-section" open>
            <summary><span>Fleet</span><b>{snapshot.vehicles?.length || 0} vehicles</b></summary>
            <div className="section-body">
              <button className="spawn-random-button wide" onClick={spawnRandomVehicle}
                disabled={loading || spawningRandomVehicle || buildings.length < 2}>
                {spawningRandomVehicle ? 'Spawning…' : 'Spawn random test vehicle'}
              </button>
              {lastRandomSpawn ? <p className="spawn-result" role="status">
                <strong>{lastRandomSpawn.vehicle.id}</strong>
                <span>{lastRandomSpawn.type} · {buildingLabel(
                  buildingById.get(lastRandomSpawn.startBuildingId),
                  intersectionById,
                )} → {buildingLabel(
                  buildingById.get(lastRandomSpawn.destinationBuildingId),
                  intersectionById,
                )}</span>
                {snapshot.status === 'RUNNING' ? <small>Live: movement begins on simulation ticks.</small>
                  : <small>Press Start or Resume to move this vehicle.</small>}
              </p> : <p className="spawn-hint">Creates a motorcycle/car at a home using a citywide registered-fleet proxy, then drives it to a reachable building.</p>}
              <div className="three-fields">
                <Field label="Type"><select value={vehicleType} onChange={(e) => setVehicleType(e.target.value)}><option>CAR</option><option>MOTORCYCLE</option><option>BUS</option><option>EMERGENCY</option></select></Field>
                <Field label="From building"><select value={vehicleStart} onChange={(e) => setVehicleStart(e.target.value)}><option value="">Choose place</option>{buildingOptionNodes}</select></Field>
                <Field label="To building"><select value={vehicleEnd} onChange={(e) => setVehicleEnd(e.target.value)}><option value="">Choose place</option>{buildingOptionNodes}</select></Field>
              </div>
              <button className="primary wide" onClick={addVehicle}
                disabled={!vehicleStart || !vehicleEnd || vehicleStart === vehicleEnd}>Add vehicle</button>
              <div className="entity-list">{snapshot.vehicles?.map((vehicle) => <button key={vehicle.id} onClick={() => dashboard.setSelected({ kind: 'vehicle', data: vehicle })}><i className={`entity-dot ${vehicle.status.toLowerCase()}`} /><span><strong>{vehicle.id}</strong><small>{vehicle.type} · {vehicle.status}</small></span><b>{Math.round((vehicle.roadProgress || 0) * 100)}%</b></button>)}</div>
            </div>
          </details>

          <NetworkEditor
            dashboard={dashboard}
            intersections={intersections}
            status={snapshot.status}
            selected={selected}
            onSelect={dashboard.setSelected}
            captureIntersection={captureIntersection}
            setCaptureIntersection={setCaptureIntersection}
            draftPoint={draftPoint}
            setDraftPoint={setDraftPoint}
            onHighlightsChange={setHighlightedRoadIds}
          />

          <details className="rail-section">
            <summary><span>Incidents</span><b>{activeEvents.length} active</b></summary>
            <div className="section-body">
              <div className="three-fields">
                <Field label="Type"><select value={eventType} onChange={(e) => setEventType(e.target.value)}>{EVENT_TYPES.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></Field>
                <Field label="Road"><select value={eventRoad} onChange={(e) => setEventRoad(e.target.value)}><option value="">Choose street and direction</option>{roadOptionNodes}</select></Field>
                <Field label="Duration (s)"><input type="number" min="1" max="3600" value={eventDuration} onChange={(e) => setEventDuration(e.target.value)} /></Field>
              </div>
              {eventType === 'ACCIDENT' || eventType === 'BREAKDOWN' ? <div className="three-fields">
                <Field label="Position (%)"><input type="number" min="0" max="100" value={eventPositionPercent}
                  onChange={(event) => setEventPositionPercent(event.target.value)} /></Field>
                <Field label="Lane (optional)"><input type="number" min="0" value={eventLaneIndex}
                  placeholder="All" onChange={(event) => setEventLaneIndex(event.target.value)} /></Field>
                {eventType === 'BREAKDOWN' ? <Field label="Vehicle"><select value={eventVehicleId}
                  onChange={(event) => {
                    setEventVehicleId(event.target.value)
                    const vehicleRoadId = snapshot.vehicles?.find((vehicle) => vehicle.id === event.target.value)?.occupiedRoadId
                    if (vehicleRoadId) setEventRoad(vehicleRoadId)
                  }}><option value="">Choose</option>
                  {snapshot.vehicles?.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.id}</option>)}</select></Field> : <span />}
              </div> : null}
              <button className="primary wide" onClick={addEvent}
                disabled={!eventRoad || (eventType === 'BREAKDOWN' && !eventVehicleId)}>Schedule incident</button>
            </div>
          </details>

          <details className="rail-section">
            <summary><span>Inspector</span><b>{selected ? selected.kind : 'Nothing selected'}</b></summary>
            <div className="section-body"><Inspector selected={selected} /></div>
          </details>

          <details className="rail-section performance-panel">
            <summary><span>Performance</span><b>{formatDuration(traffic.averageTravelTimeSeconds)} avg trip</b></summary>
            <div className="section-body">
              <div className="performance-grid">
                <Metric label="Total travel" value={formatDuration(traffic.totalTravelTimeSeconds)} />
                <Metric label="Route length" value={`${Number(traffic.averageRouteLengthKm || 0).toFixed(2)} km`} />
                <Metric label="Vehicle density" value={`${Math.round(Number(traffic.averageVehicleDensity || 0) * 100)}%`} />
                <Metric label="Waiting" value={traffic.waitingVehicles || 0} />
              </div>
              <div className="algorithm-stat-list">{(statistics.algorithms || []).map((item) => (
                <div key={item.algorithmName}>
                  <strong>{item.algorithmName}</strong>
                  <span>{Number(item.averageComputationTime || 0).toFixed(3)} ms</span>
                  <span>{Number(item.averagePathOptimality || 0).toFixed(1)}% optimal</span>
                  <span>{item.routeRecalculationCount || 0} reroutes</span>
                </div>
              ))}</div>
            </div>
          </details>

          <ExperimentPanel />
        </aside>
      </section>

      <TimelineScrubber timeline={timeline} frame={viewFrame} viewingHistory={viewingHistory}
        loading={historyLoading} onSeek={dashboard.seekFrame} onLive={dashboard.returnToLive}
        roadCount={roads.length} intersectionCount={intersections.length}
        incidentCount={viewActiveEvents.length} />
    </main>
  )
}
