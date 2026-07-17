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

const EMPTY_SNAPSHOT = { tick: 0, status: 'READY', vehicles: [], trafficEvents: [], roadStates: [] }

export function useTrafficDashboard() {
  const [intersections, setIntersections] = useState([])
  const [roads, setRoads] = useState([])
  const [scenarios, setScenarios] = useState([])
  const [activeScenario, setActiveScenario] = useState('small-city')
  const [snapshot, setSnapshot] = useState(EMPTY_SNAPSHOT)
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
  const tickingRef = useRef(false)

  const refreshNetwork = useCallback(async () => {
    const [nextIntersections, nextRoads] = await Promise.all([
      networkApi.getAllIntersections(),
      networkApi.getAllRoads(),
    ])
    setIntersections(nextIntersections)
    setRoads(nextRoads)
  }, [])

  const refreshLiveData = useCallback(async () => {
    const [nextSnapshot, nextStatistics] = await Promise.all([
      simulationApi.snapshot(),
      statisticsApi.all(),
    ])
    setSnapshot(nextSnapshot)
    setStatistics(nextStatistics)
    return nextSnapshot
  }, [])

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
  }, [])

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

  const activateScenario = useCallback(async (id) => {
    setAutoRun(false)
    setLoading(true)
    setError('')
    try {
      await scenarioApi.activate(id)
      setActiveScenario(id)
      setRoute(null)
      setComparison([])
      setSelected(null)
      await Promise.all([refreshNetwork(), refreshLiveData()])
    } catch (nextError) {
      setError(nextError.message)
    } finally {
      setLoading(false)
    }
  }, [refreshLiveData, refreshNetwork])

  const initializeDemo = useCallback(async () => {
    if (intersections.length < 2) return
    const first = intersections[0].id
    const last = intersections.at(-1).id
    const middle = intersections[Math.floor(intersections.length / 2)].id
    await runAction(() => simulationApi.initialize([
      { id: 'CAR-01', type: 'CAR', startIntersectionId: first, destinationIntersectionId: last },
      { id: 'BUS-01', type: 'BUS', startIntersectionId: middle, destinationIntersectionId: first },
      { id: 'EMS-01', type: 'EMERGENCY', startIntersectionId: first, destinationIntersectionId: middle },
    ]))
  }, [intersections, runAction])

  const updateStatus = useCallback(async (action) => {
    const status = await runAction(action, { refresh: false })
    setSnapshot((current) => ({ ...current, status: status.status, tick: status.currentTick }))
    return status
  }, [runAction])

  const advanceTick = useCallback(async () => {
    if (tickingRef.current) return
    tickingRef.current = true
    try {
      const nextSnapshot = await runAction(simulationApi.tick, { refresh: false })
      setSnapshot(nextSnapshot)
      statisticsApi.all().then(setStatistics).catch((nextError) => setError(nextError.message))
    } finally {
      tickingRef.current = false
    }
  }, [runAction])

  const roadStateById = useMemo(
    () => new Map((snapshot.roadStates || []).map((road) => [road.id, road])),
    [snapshot.roadStates],
  )
  const liveRoads = useMemo(
    () => roads.map((road) => ({ ...road, ...roadStateById.get(road.id) })),
    [roadStateById, roads],
  )

  const editNetwork = useCallback(async (action) => {
    const result = await runAction(action, { refresh: false })
    setRoute(null)
    setComparison([])
    await Promise.all([refreshNetwork(), refreshLiveData()])
    return result
  }, [refreshLiveData, refreshNetwork, runAction])

  return {
    intersections,
    roads: liveRoads,
    scenarios,
    activeScenario,
    snapshot,
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
    setSelected,
    setRoute,
    setAutoRun,
    setTickDelay,
    activateScenario,
    initializeDemo,
    start: async () => { setAutoRun(true); return updateStatus(simulationApi.start) },
    pause: async () => { setAutoRun(false); return updateStatus(simulationApi.pause) },
    resume: async () => { setAutoRun(true); return updateStatus(simulationApi.resume) },
    reset: async () => { setAutoRun(false); return runAction(simulationApi.reset) },
    advanceTick,
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
