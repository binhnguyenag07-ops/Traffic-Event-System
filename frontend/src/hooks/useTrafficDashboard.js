import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import {
  networkApi,
  routingApi,
  scenarioApi,
  simulationApi,
  statisticsApi,
  trafficEventApi,
  vehicleApi,
} from '../api/trafficAPI'
import { buildDemoTraffic, createBoundaryTripPlanner, replacementDelayMs } from '../demoTraffic'
import { randomBoundarySpawnPolicy } from '../spawnPolicies'

const EMPTY_SNAPSHOT = {
  timelineId: null, frameId: null, tick: 0, elapsedSeconds: 0, status: 'READY',
  vehicles: [], trafficEvents: [], roadStates: [], trafficSignals: [],
}
const EMPTY_TIMELINE = {
  timelineId: null, firstElapsedSeconds: 0, lastElapsedSeconds: 0,
  retainedFrameCount: 0, retentionLimit: 0, recordedSeconds: [],
}
const DEMO_VEHICLE_IDS_KEY = 'traffic-demo-vehicle-ids-v1'

function readDemoVehicleIds() {
  try {
    const stored = JSON.parse(window.sessionStorage.getItem(DEMO_VEHICLE_IDS_KEY) || '[]')
    return Array.isArray(stored) ? stored.filter((id) => typeof id === 'string') : []
  } catch {
    return []
  }
}

function persistDemoVehicleIds(ids) {
  try {
    window.sessionStorage.setItem(DEMO_VEHICLE_IDS_KEY, JSON.stringify([...ids]))
  } catch {
    // The live demo still works when storage is unavailable.
  }
}

export function useTrafficDashboard() {
  const [intersections, setIntersections] = useState([])
  const [roads, setRoads] = useState([])
  const [scenarios, setScenarios] = useState([])
  const [activeScenario, setActiveScenario] = useState('district-1')
  const [snapshot, setSnapshot] = useState(EMPTY_SNAPSHOT)
  const [timeline, setTimeline] = useState(EMPTY_TIMELINE)
  const [historicalFrame, setHistoricalFrame] = useState(null)
  const [historyLoading, setHistoryLoading] = useState(false)
  const [statistics, setStatistics] = useState({ traffic: {}, algorithms: [] })
  const [algorithms, setAlgorithms] = useState([])
  const [route, setRoute] = useState(null)
  const [comparison, setComparison] = useState([])
  const [selected, setSelected] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [autoRun, setAutoRun] = useState(false)
  const [tickDelay, setTickDelay] = useState(1000)
  const [streamConnected, setStreamConnected] = useState(false)
  const [importSummary, setImportSummary] = useState(null)
  const tickingRef = useRef(false)
  const startupRef = useRef(false)
  const demoVehicleIdsRef = useRef(null)
  if (demoVehicleIdsRef.current === null) demoVehicleIdsRef.current = new Set(readDemoVehicleIds())
  const pendingReplacementsRef = useRef(new Map())
  const replacementsInFlightRef = useRef(new Set())
  const nextDemoTripRef = useRef(0)
  const pauseStartedAtRef = useRef(null)
  const demoGenerationRef = useRef(0)
  const timelineIdRef = useRef(null)
  const timelineRequestRef = useRef(0)
  const historicalFrameRef = useRef(null)
  const historyRequestRef = useRef(0)
  const boundaryPlanner = useMemo(
    () => createBoundaryTripPlanner(intersections, roads),
    [intersections, roads],
  )

  const refreshNetwork = useCallback(async () => {
    const [nextIntersections, nextRoads] = await Promise.all([
      networkApi.getAllIntersections(),
      networkApi.getAllRoads(),
    ])
    setIntersections(nextIntersections)
    setRoads(nextRoads)
  }, [])

  const applyTimeline = useCallback((nextTimeline) => {
    const currentTimelineId = timelineIdRef.current
    const activeHistoricalFrame = historicalFrameRef.current
    const changedTimeline = currentTimelineId && nextTimeline.timelineId !== currentTimelineId
    const evictedFrame = activeHistoricalFrame
      && nextTimeline.timelineId === activeHistoricalFrame.timelineId
      && activeHistoricalFrame.elapsedSeconds < nextTimeline.firstElapsedSeconds - 1e-9
    if (changedTimeline || evictedFrame) {
      historyRequestRef.current += 1
      historicalFrameRef.current = null
      setHistoricalFrame(null)
    }
    timelineIdRef.current = nextTimeline.timelineId
    setTimeline(nextTimeline)
  }, [])

  const refreshTimeline = useCallback(async () => {
    const requestId = timelineRequestRef.current + 1
    timelineRequestRef.current = requestId
    const nextTimeline = await simulationApi.frameTimeline()
    if (requestId === timelineRequestRef.current) applyTimeline(nextTimeline)
    return nextTimeline
  }, [applyTimeline])

  const refreshLiveData = useCallback(async () => {
    const [nextSnapshot, nextStatistics] = await Promise.all([
      simulationApi.snapshot(),
      statisticsApi.all(),
      refreshTimeline(),
    ])
    setSnapshot(nextSnapshot)
    setStatistics(nextStatistics)
    return nextSnapshot
  }, [refreshTimeline])

  const refreshAll = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      await Promise.all([refreshNetwork(), refreshLiveData()])
    } catch (nextError) {
      setError(nextError.message)
    } finally {
      setLoading(false)
    }
  }, [refreshLiveData, refreshNetwork])

  useEffect(() => {
    Promise.all([scenarioApi.list(), routingApi.algorithms()])
      .then(([nextScenarios, nextAlgorithms]) => {
        setScenarios(nextScenarios)
        setAlgorithms(nextAlgorithms)
      })
      .catch((nextError) => setError(nextError.message))
    refreshAll()
  }, [refreshAll])

  useEffect(() => {
    const stream = new EventSource(simulationApi.streamUrl())
    const handleSnapshot = (event) => {
      const nextSnapshot = JSON.parse(event.data)
      setSnapshot(nextSnapshot)
      setStreamConnected(true)
      if (nextSnapshot.timelineId
        && timelineIdRef.current
        && nextSnapshot.timelineId !== timelineIdRef.current) {
        historicalFrameRef.current = null
        setHistoricalFrame(null)
      }
      refreshTimeline().catch(() => {})
      if (nextSnapshot.tick % 2 === 0) {
        statisticsApi.all().then(setStatistics).catch(() => {})
      }
    }
    stream.addEventListener('snapshot', handleSnapshot)
    stream.onopen = () => setStreamConnected(true)
    stream.onerror = () => setStreamConnected(false)
    return () => {
      stream.removeEventListener('snapshot', handleSnapshot)
      stream.close()
    }
  }, [refreshTimeline])

  useEffect(() => {
    if (streamConnected) return undefined
    const timer = window.setInterval(() => {
      refreshLiveData().catch(() => {})
    }, 2000)
    return () => window.clearInterval(timer)
  }, [refreshLiveData, streamConnected])

  const runAction = useCallback(async (action, { refresh = true } = {}) => {
    setError('')
    try {
      const result = await action()
      if (refresh) await refreshLiveData()
      return result
    } catch (nextError) {
      setError(nextError.message)
      throw nextError
    }
  }, [refreshLiveData])

  const clearDemoLifecycle = useCallback(() => {
    demoGenerationRef.current += 1
    demoVehicleIdsRef.current.clear()
    pendingReplacementsRef.current.clear()
    replacementsInFlightRef.current.clear()
    nextDemoTripRef.current = 0
    pauseStartedAtRef.current = null
    persistDemoVehicleIds(demoVehicleIdsRef.current)
  }, [])

  const activateScenario = useCallback(async (id) => {
    setAutoRun(false)
    clearDemoLifecycle()
    setLoading(true)
    setError('')
    try {
      await scenarioApi.activate(id)
      setActiveScenario(id)
      setImportSummary(null)
      setRoute(null)
      setComparison([])
      setSelected(null)
      await Promise.all([refreshNetwork(), refreshLiveData()])
    } catch (nextError) {
      setError(nextError.message)
    } finally {
      setLoading(false)
    }
  }, [clearDemoLifecycle, refreshLiveData, refreshNetwork])

  const importMap = useCallback(async (file) => {
    setAutoRun(false)
    clearDemoLifecycle()
    setLoading(true)
    setError('')
    try {
      const result = await scenarioApi.importMap(file)
      setImportSummary(result)
      setActiveScenario('imported')
      setScenarios((current) => [
        ...current.filter((scenario) => scenario.id !== 'imported'),
        { id: 'imported', name: result.filename, description: 'Imported map' },
      ])
      setRoute(null)
      setComparison([])
      setSelected(null)
      await Promise.all([refreshNetwork(), refreshLiveData()])
      return result
    } catch (nextError) {
      setError(nextError.message)
      throw nextError
    } finally {
      setLoading(false)
    }
  }, [clearDemoLifecycle, refreshLiveData, refreshNetwork])

  const initializeDemo = useCallback(async () => {
    const demo = buildDemoTraffic(intersections, roads)
    if (!demo.vehicles.length) return
    demoVehicleIdsRef.current = new Set(demo.vehicles.map((vehicle) => vehicle.id))
    pendingReplacementsRef.current.clear()
    replacementsInFlightRef.current.clear()
    persistDemoVehicleIds(demoVehicleIdsRef.current)
    await runAction(() => simulationApi.initialize(demo.vehicles, demo.events))
  }, [intersections, roads, runAction])

  const startLiveDemo = useCallback(async () => {
    const demo = buildDemoTraffic(intersections, roads)
    if (!demo.vehicles.length) return

    demoVehicleIdsRef.current = new Set(demo.vehicles.map((vehicle) => vehicle.id))
    pendingReplacementsRef.current.clear()
    replacementsInFlightRef.current.clear()
    nextDemoTripRef.current = 0
    pauseStartedAtRef.current = null
    persistDemoVehicleIds(demoVehicleIdsRef.current)
    setLoading(true)
    setError('')
    try {
      await simulationApi.setParameters(3, 500)
      await simulationApi.initialize(demo.vehicles, demo.events)
      for (let tick = 0; tick < 4; tick += 1) {
        await simulationApi.tick()
      }
      await simulationApi.start()
      setTickDelay(500)
      setAutoRun(true)
      await refreshLiveData()
    } catch (nextError) {
      setError(nextError.message)
    } finally {
      setLoading(false)
    }
  }, [intersections, refreshLiveData, roads])

  const replaceArrivedDemoVehicles = useCallback(async (vehicleIds) => {
    const replacementErrors = []
    await Promise.all(vehicleIds.map(async (vehicleId) => {
      const pending = pendingReplacementsRef.current.get(vehicleId)
      if (!pending || replacementsInFlightRef.current.has(vehicleId)) return
      const generation = demoGenerationRef.current
      replacementsInFlightRef.current.add(vehicleId)
      try {
        if (!pending.replacementId) {
          const trip = boundaryPlanner.planTrip(pending.tripNumber)
          if (!trip) throw new Error('No reachable boundary-to-boundary trip is available.')
          const replacement = await vehicleApi.create(
            pending.type,
            trip.startIntersectionId,
            trip.destinationIntersectionId,
          )
          if (generation !== demoGenerationRef.current) {
            await vehicleApi.remove(replacement.id).catch(() => {})
            return
          }
          pending.replacementId = replacement.id
          demoVehicleIdsRef.current.add(replacement.id)
          persistDemoVehicleIds(demoVehicleIdsRef.current)
        }
        try {
          await vehicleApi.remove(vehicleId)
        } catch (removeError) {
          // The simulator may expire an arrived vehicle before this delayed replacement runs.
          // Treat that lifecycle race as an already-completed delete, but surface every other error.
          if (removeError.status !== 404) throw removeError
        }
        demoVehicleIdsRef.current.delete(vehicleId)
        pendingReplacementsRef.current.delete(vehicleId)
        persistDemoVehicleIds(demoVehicleIdsRef.current)
      } catch (nextError) {
        pending.dueAt = Date.now() + 1000
        replacementErrors.push(nextError)
      } finally {
        replacementsInFlightRef.current.delete(vehicleId)
      }
    }))
    if (replacementErrors.length > 0) {
      setError(`Could not replace boundary traffic: ${replacementErrors[0].message}`)
    } else if (vehicleIds.length > 0) {
      setError((current) => (
        current.startsWith('Could not replace boundary traffic:') ? '' : current
      ))
    }
    await refreshLiveData()
  }, [boundaryPlanner, refreshLiveData])

  const updateStatus = useCallback(async (action) => {
    const status = await runAction(action, { refresh: false })
    setSnapshot((current) => ({ ...current, status: status.status, tick: status.currentTick }))
    return status
  }, [runAction])

  useEffect(() => {
    if (loading || startupRef.current || intersections.length < 2 || roads.length === 0) return
    startupRef.current = true

    const activeVehicles = snapshot.vehicles.filter((item) => item.status !== 'ARRIVED')
    if (activeVehicles.length > 0) {
      if (snapshot.status === 'RUNNING') {
        // A page reload reconnects to the server-owned playback. Keep the local
        // boundary-fleet lifecycle active instead of silently stopping replacements.
        setAutoRun(true)
      } else if (snapshot.status === 'READY') {
        setAutoRun(true)
        updateStatus(simulationApi.start).catch(() => {})
      } else if (snapshot.status === 'PAUSED') {
        setAutoRun(true)
        updateStatus(simulationApi.resume).catch(() => {})
      }
      return
    }

    startLiveDemo()
  }, [intersections.length, loading, roads.length, snapshot.status, snapshot.vehicles, startLiveDemo, updateStatus])

  useEffect(() => {
    let trackedIdsChanged = false
    snapshot.vehicles.forEach((vehicle) => {
      if (/^(CAR|MOTO|BUS|EMS)-\d+$/.test(vehicle.id) && !demoVehicleIdsRef.current.has(vehicle.id)) {
        demoVehicleIdsRef.current.add(vehicle.id)
        trackedIdsChanged = true
      }
      if (vehicle.status === 'ARRIVED'
        && demoVehicleIdsRef.current.has(vehicle.id)
        && !pendingReplacementsRef.current.has(vehicle.id)) {
        const tripNumber = nextDemoTripRef.current
        nextDemoTripRef.current += 1
        pendingReplacementsRef.current.set(vehicle.id, {
          type: vehicle.type,
          tripNumber,
          dueAt: Date.now() + replacementDelayMs(tripNumber),
          replacementId: null,
        })
      }
    })
    if (trackedIdsChanged) persistDemoVehicleIds(demoVehicleIdsRef.current)
    if (!autoRun || snapshot.status !== 'RUNNING') return

    const now = Date.now()
    const dueVehicleIds = [...pendingReplacementsRef.current.entries()]
      .filter(([, pending]) => pending.dueAt <= now)
      .map(([vehicleId]) => vehicleId)
    if (dueVehicleIds.length) replaceArrivedDemoVehicles(dueVehicleIds)
  }, [autoRun, replaceArrivedDemoVehicles, snapshot.status, snapshot.vehicles])

  useEffect(() => () => {
    demoGenerationRef.current += 1
    pendingReplacementsRef.current.clear()
    replacementsInFlightRef.current.clear()
  }, [])

  const advanceTick = useCallback(async () => {
    if (tickingRef.current) return
    tickingRef.current = true
    try {
      const nextSnapshot = await runAction(simulationApi.tick, { refresh: false })
      setSnapshot(nextSnapshot)
      statisticsApi.all().then(setStatistics).catch((nextError) => setError(nextError.message))
      refreshTimeline().catch((nextError) => setError(nextError.message))
    } finally {
      tickingRef.current = false
    }
  }, [refreshTimeline, runAction])

  const seekFrame = useCallback(async (elapsedSeconds) => {
    const requestId = historyRequestRef.current + 1
    historyRequestRef.current = requestId
    setHistoryLoading(true)
    setError('')
    try {
      const frame = await simulationApi.frameAt(elapsedSeconds)
      if (requestId !== historyRequestRef.current) return null
      if (timelineIdRef.current && frame.timelineId !== timelineIdRef.current) {
        throw new Error('The simulation timeline changed while loading this frame.')
      }
      historicalFrameRef.current = frame
      setHistoricalFrame(frame)
      setSelected(null)
      return frame
    } catch (nextError) {
      if (requestId === historyRequestRef.current) setError(nextError.message)
      return null
    } finally {
      if (requestId === historyRequestRef.current) setHistoryLoading(false)
    }
  }, [])

  const returnToLive = useCallback(() => {
    historyRequestRef.current += 1
    historicalFrameRef.current = null
    setHistoricalFrame(null)
    setHistoryLoading(false)
    setSelected(null)
  }, [])

  const spawnRandomVehicle = useCallback(async () => {
    const request = randomBoundarySpawnPolicy.plan({ boundaryPlanner })
    if (historicalFrameRef.current) returnToLive()
    const vehicle = await runAction(() => vehicleApi.create(
      request.type,
      request.startIntersectionId,
      request.destinationIntersectionId,
    ))
    return { ...request, vehicle }
  }, [boundaryPlanner, returnToLive, runAction])

  const viewFrame = historicalFrame || snapshot

  const roadStateById = useMemo(
    () => new Map((snapshot.roadStates || []).map((road) => [road.id, road])),
    [snapshot.roadStates],
  )
  const liveRoads = useMemo(
    () => roads.map((road) => ({ ...road, ...roadStateById.get(road.id) })),
    [roadStateById, roads],
  )
  const viewRoadStateById = useMemo(
    () => new Map((viewFrame.roadStates || []).map((road) => [road.id, road])),
    [viewFrame.roadStates],
  )
  const viewRoads = useMemo(
    () => roads.map((road) => ({ ...road, ...viewRoadStateById.get(road.id) })),
    [roads, viewRoadStateById],
  )

  const editNetwork = useCallback(async (action) => {
    const result = await runAction(action, { refresh: false })
    setRoute(null)
    setComparison([])
    await Promise.all([refreshNetwork(), refreshLiveData()])
    return result
  }, [refreshLiveData, refreshNetwork, runAction])

  const pauseSimulation = useCallback(async () => {
    setAutoRun(false)
    pauseStartedAtRef.current = Date.now()
    try {
      return await updateStatus(simulationApi.pause)
    } catch (nextError) {
      pauseStartedAtRef.current = null
      throw nextError
    }
  }, [updateStatus])

  const resumeSimulation = useCallback(async () => {
    const status = await updateStatus(simulationApi.resume)
    if (pauseStartedAtRef.current != null) {
      const pausedFor = Date.now() - pauseStartedAtRef.current
      pendingReplacementsRef.current.forEach((pending) => { pending.dueAt += pausedFor })
    }
    pauseStartedAtRef.current = null
    setAutoRun(true)
    return status
  }, [updateStatus])

  const resetSimulation = useCallback(async () => {
    const hadDemoFleet = demoVehicleIdsRef.current.size > 0
    setAutoRun(false)
    clearDemoLifecycle()
    const status = await runAction(simulationApi.reset)
    if (hadDemoFleet) {
      const demo = buildDemoTraffic(intersections, roads)
      demoVehicleIdsRef.current = new Set(demo.vehicles.map((vehicle) => vehicle.id))
      persistDemoVehicleIds(demoVehicleIdsRef.current)
    }
    return status
  }, [clearDemoLifecycle, intersections, roads, runAction])

  return {
    intersections,
    roads: liveRoads,
    viewRoads,
    scenarios,
    activeScenario,
    snapshot,
    viewFrame,
    timeline,
    viewingHistory: historicalFrame !== null,
    historyLoading,
    statistics,
    algorithms,
    route,
    comparison,
    selected,
    loading,
    error,
    autoRun,
    tickDelay,
    streamConnected,
    importSummary,
    setSelected,
    setRoute,
    setAutoRun,
    setTickDelay,
    activateScenario,
    importMap,
    initializeDemo,
    start: async () => { setAutoRun(true); return updateStatus(simulationApi.start) },
    pause: pauseSimulation,
    resume: resumeSimulation,
    reset: resetSimulation,
    advanceTick,
    seekFrame,
    returnToLive,
    setPlaybackInterval: async (milliseconds) => {
      setTickDelay(milliseconds)
      const status = await runAction(
        () => simulationApi.setParameters(10, milliseconds),
        { refresh: false },
      )
      setSnapshot((current) => ({ ...current, status: status.status }))
    },
    findRoute: async (start, destination, preference, algorithm) => {
      const result = await runAction(
        () => routingApi.findRoute(start, destination, preference, algorithm),
        { refresh: false },
      )
      setRoute(result)
      return result
    },
    compareRoutes: async (start, destination, preference) => {
      const result = await runAction(() => routingApi.compare(start, destination, preference), { refresh: false })
      setComparison(result.results || [])
      const firstSuccess = result.results?.find((item) => item.success)
      if (firstSuccess) setRoute(firstSuccess.result)
    },
    createVehicle: async (type, start, destination) => runAction(() => vehicleApi.create(type, start, destination)),
    spawnRandomVehicle,
    removeVehicle: async (id) => runAction(() => vehicleApi.remove(id)),
    createEvent: async (payload) => runAction(() => trafficEventApi.create(payload)),
    updateEvent: async (id, status) => runAction(() => trafficEventApi.updateStatus(id, status)),
    removeEvent: async (id) => runAction(() => trafficEventApi.remove(id)),
    addIntersection: (payload) => editNetwork(() => networkApi.addIntersection(payload)),
    removeIntersection: (id) => editNetwork(() => networkApi.removeIntersection(id)),
    addRoad: (payload) => editNetwork(() => networkApi.addRoad(payload)),
    removeRoad: (id) => editNetwork(() => networkApi.removeRoad(id)),
    updateRoadCondition: (id, payload) => editNetwork(() => networkApi.updateRoadCondition(id, payload)),
    connectedRoads: networkApi.connectedRoads,
    exportNetwork: networkApi.exportNetwork,
    refreshAll,
  }
}
