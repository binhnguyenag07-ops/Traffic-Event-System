const numberOr = (value, fallback = 0) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}

const textOr = (value, fallback) => (value === undefined || value === null ? fallback : String(value))

export class Intersection {
  constructor(data = {}) {
    this.id = textOr(data.id ?? data.intersectionId, '')
    this.name = data.name || this.id
    this.x = numberOr(data.x ?? data.longitude ?? data.location?.x, 0)
    this.y = numberOr(data.y ?? data.latitude ?? data.location?.y, 0)
    this.locationType = data.locationType || data.type || 'NORMAL'
  }
}

export class Road {
  constructor(data = {}) {
    this.id = textOr(data.id ?? data.roadId, '')
    this.startIntersectionId = textOr(
      data.startIntersectionId ?? data.fromIntersectionId ?? data.sourceId ?? data.startId,
      ''
    )
    this.endIntersectionId = textOr(
      data.endIntersectionId ?? data.toIntersectionId ?? data.targetId ?? data.endId,
      ''
    )
    this.distance = numberOr(data.distance ?? data.length, 1)
    this.travelTime = numberOr(data.travelTime ?? data.estimatedTravelTime, this.distance)
    this.capacity = numberOr(data.capacity, 10)
    this.name = textOr(data.name, this.id)
    this.physicalRoadId = textOr(data.physicalRoadId, this.id)
    this.laneCount = Math.max(1, Math.trunc(numberOr(data.laneCount, 1)))
    this.laneWidthMetres = numberOr(data.laneWidthMetres, 3)
    this.roadWidthMetres = numberOr(data.roadWidthMetres, this.laneCount * this.laneWidthMetres)
    this.roadClass = data.roadClass || 'UNCLASSIFIED'
    this.geometry = Array.isArray(data.geometry) ? data.geometry : []
    this.status = data.status || data.roadStatus || 'OPEN'
    this.condition = data.condition || {}
    this.congestionLevel = numberOr(
      data.congestionLevel ?? data.condition?.congestionLevel ?? data.congestion,
      0
    )
  }

  getStatusColor() {
    if (this.status === 'CLOSED' || this.status === 'BLOCKED') return '#dc2626'
    if (this.congestionLevel >= 0.75) return '#ea580c'
    if (this.congestionLevel >= 0.4) return '#d97706'
    return '#2563eb'
  }

  getTravelCost(algorithm = 'dijkstra') {
    if (this.status === 'CLOSED' || this.status === 'BLOCKED') return Number.POSITIVE_INFINITY

    const normalizedAlgorithm = algorithm.toLowerCase()
    const congestionMultiplier = 1 + this.congestionLevel * 2
    if (normalizedAlgorithm === 'bfs' || normalizedAlgorithm === 'shortest_distance') return 1
    if (normalizedAlgorithm === 'shortest_time') return this.travelTime
    if (
      normalizedAlgorithm === 'congestion_aware' ||
      normalizedAlgorithm.includes('astar') ||
      normalizedAlgorithm.includes('a*')
    ) {
      return this.travelTime * congestionMultiplier
    }
    return this.distance * congestionMultiplier
  }
}

export class RoadNetwork {
  constructor(data = {}) {
    const intersections = data.intersections || data.nodes || []
    const roads = data.roads || data.edges || []

    this.id = data.id || 'network'
    this.name = data.name || 'Traffic Network'
    this.intersections = intersections.map((intersection) =>
      intersection instanceof Intersection ? intersection : new Intersection(intersection)
    )
    this.roads = roads.map((road) => (road instanceof Road ? road : new Road(road)))
  }

  getStatistics() {
    const totalCongestion = this.roads.reduce((sum, road) => sum + road.congestionLevel, 0)
    return {
      intersections: this.intersections.length,
      roads: this.roads.length,
      averageCongestion: this.roads.length ? totalCongestion / this.roads.length : 0,
      closedRoads: this.roads.filter((road) => road.status === 'CLOSED' || road.status === 'BLOCKED').length,
    }
  }
}

export class Route {
  constructor(data = {}) {
    this.algorithm = data.algorithm || data.algorithmName || ''
    this.computationTimeMillis = numberOr(data.computationTimeMillis, 0)
    this.visitedNodes = numberOr(data.visitedNodes, 0)
    this.steps = (data.steps || data.routeSteps || []).map((step) => ({
      fromIntersectionId: textOr(step.fromIntersectionId ?? step.from ?? step.startIntersectionId, ''),
      toIntersectionId: textOr(step.toIntersectionId ?? step.to ?? step.endIntersectionId, ''),
      roadId: step.roadId,
      cost: numberOr(step.cost ?? step.distance ?? step.travelTime, 1),
    }))
    this.totalDistance = numberOr(data.totalDistance ?? data.distance, this.steps.length)
    this.estimatedTravelTime = numberOr(
      data.estimatedTravelTime ?? data.travelTime ?? data.totalTime,
      this.totalDistance
    )
    this.totalCost = numberOr(data.totalCost ?? data.cost, this.totalDistance)
  }

  getLength() {
    return this.steps.length
  }
}

const getOutgoingRoads = (network, intersectionId) =>
  (network?.roads || []).filter((road) => road.startIntersectionId === intersectionId)

const getIntersectionById = (network, intersectionId) =>
  (network?.intersections || []).find((intersection) => intersection.id === intersectionId)

const estimateDistance = (network, fromId, toId) => {
  const from = getIntersectionById(network, fromId)
  const to = getIntersectionById(network, toId)
  if (!from || !to) return 0
  return Math.hypot(from.x - to.x, from.y - to.y) / 110
}

const reconstructLocalRoute = (network, startId, endId, previous, previousRoad, algorithm) => {
  const steps = []
  let currentId = endId

  while (currentId !== startId) {
    const fromId = previous.get(currentId)
    const roadId = previousRoad.get(currentId)
    if (!fromId || !roadId) break

    const road = (network.roads || []).find((candidate) => candidate.id === roadId)
    steps.unshift({
      fromIntersectionId: fromId,
      toIntersectionId: currentId,
      roadId,
      cost: road?.getTravelCost?.(algorithm) ?? road?.distance ?? 1,
    })
    currentId = fromId
  }

  const totalDistance = steps.reduce((sum, step) => {
    const road = (network.roads || []).find((candidate) => candidate.id === step.roadId)
    return sum + (road?.distance ?? step.cost)
  }, 0)
  const totalCost = steps.reduce((sum, step) => sum + step.cost, 0)

  return new Route({
    algorithm,
    steps,
    totalDistance,
    estimatedTravelTime: totalCost,
    totalCost,
  })
}

export function findLocalRoute(network, startId, endId, algorithm = 'dijkstra') {
  if (!network || !startId || !endId) {
    throw new Error('A loaded network, start, and destination are required')
  }

  if (startId === endId) return new Route()

  const normalizedAlgorithm = algorithm.toLowerCase()
  const useBreadthFirst = normalizedAlgorithm === 'bfs' || normalizedAlgorithm === 'shortest_distance'
  const useAStar = normalizedAlgorithm === 'astar' || normalizedAlgorithm === 'a*' || normalizedAlgorithm === 'congestion_aware'
  const frontier = [{ id: startId, priority: 0 }]
  const previous = new Map()
  const previousRoad = new Map()
  const costSoFar = new Map([[startId, 0]])
  const visited = new Set()

  while (frontier.length > 0) {
    frontier.sort((a, b) => a.priority - b.priority)
    const current = frontier.shift().id

    if (current === endId) break
    if (useBreadthFirst && visited.has(current)) continue
    visited.add(current)

    for (const road of getOutgoingRoads(network, current)) {
      const nextId = road.endIntersectionId
      const stepCost = road.getTravelCost(normalizedAlgorithm)
      if (!Number.isFinite(stepCost)) continue

      const newCost = costSoFar.get(current) + stepCost
      const oldCost = costSoFar.get(nextId)
      if (oldCost !== undefined && newCost >= oldCost) continue

      costSoFar.set(nextId, newCost)
      previous.set(nextId, current)
      previousRoad.set(nextId, road.id)

      const heuristic = useAStar ? estimateDistance(network, nextId, endId) : 0
      frontier.push({
        id: nextId,
        priority: useBreadthFirst ? frontier.length : newCost + heuristic,
      })
    }
  }

  if (!previous.has(endId)) {
    throw new Error(`No route found from ${startId} to ${endId}`)
  }

  return reconstructLocalRoute(network, startId, endId, previous, previousRoad, normalizedAlgorithm)
}

export class Vehicle {
  constructor(data = {}) {
    this.id = textOr(data.id ?? data.vehicleId, '')
    this.type = data.type || data.vehicleType || 'CAR'
    this.currentIntersectionId = textOr(
      data.currentIntersectionId ?? data.startIntersectionId ?? data.locationIntersectionId,
      ''
    )
    this.destinationIntersectionId = textOr(data.destinationIntersectionId ?? data.endIntersectionId, '')
    this.status = data.status || 'READY'
    this.route = data.route || []
    this.pathHistory = data.pathHistory || (this.currentIntersectionId ? [this.currentIntersectionId] : [])
    this.laneIndex = Math.max(0, Math.trunc(numberOr(data.laneIndex, 0)))
    this.lateralOffsetMetres = numberOr(data.lateralOffsetMetres, 0)
    this.latitude = data.latitude == null ? null : numberOr(data.latitude, null)
    this.longitude = data.longitude == null ? null : numberOr(data.longitude, null)
    this.headingDegrees = data.headingDegrees == null ? null : numberOr(data.headingDegrees, null)
  }

  getTypeName() {
    return this.type.replaceAll('_', ' ')
  }

  getStatusName() {
    return this.status.replaceAll('_', ' ')
  }

  getStatusColor() {
    if (this.hasReachedDestination()) return '#16a34a'
    if (this.status === 'MOVING' || this.status === 'RUNNING') return '#2563eb'
    if (this.status === 'BLOCKED') return '#dc2626'
    return '#64748b'
  }

  getTravelDistance() {
    return Math.max(0, this.pathHistory.length - 1)
  }

  hasReachedDestination() {
    return this.currentIntersectionId === this.destinationIntersectionId || this.status === 'ARRIVED'
  }

  getProgressName() {
    return this.hasReachedDestination() ? 'Arrived' : 'Traveling'
  }
}

export const VehicleFactory = {
  createVehicle(type, startIntersectionId, destinationIntersectionId, id = crypto.randomUUID()) {
    return new Vehicle({
      id,
      type,
      currentIntersectionId: startIntersectionId,
      startIntersectionId,
      destinationIntersectionId,
      status: 'READY',
    })
  },
}

export class TrafficEvent {
  constructor(data = {}) {
    this.id = textOr(data.id ?? data.eventId, '')
    this.type = data.type || data.eventType || 'CONGESTION'
    this.affectedRoadId = textOr(data.affectedRoadId ?? data.roadId, '')
    this.startTimeSeconds = numberOr(data.startTimeSeconds ?? data.startTick, 0)
    this.durationSeconds = numberOr(data.durationSeconds ?? data.durationTicks ?? data.duration, 1)
    this.positionAlongRoad = numberOr(data.positionAlongRoad, 0.5)
    this.blockedLaneIndexes = Array.isArray(data.blockedLaneIndexes) ? data.blockedLaneIndexes : []
    this.causingVehicleId = data.causingVehicleId || null
    this.affectedVehicleId = data.affectedVehicleId || null
    this.involvedVehicleIds = Array.isArray(data.involvedVehicleIds) ? data.involvedVehicleIds : []
    this.status = data.status || 'ACTIVE'
  }

  getTypeName() {
    return this.type.replaceAll('_', ' ')
  }

  getStatusName() {
    return this.status.replaceAll('_', ' ')
  }

  getTypeColor() {
    const colors = {
      CONGESTION: '#f59e0b',
      ACCIDENT: '#dc2626',
      ROAD_CLOSURE: '#7c3aed',
      BREAKDOWN: '#be123c',
    }
    return colors[this.type] || '#64748b'
  }

  getProgressPercentage(elapsedSeconds = 0) {
    const elapsed = Math.max(0, elapsedSeconds - this.startTimeSeconds)
    return Math.min(100, (elapsed / Math.max(1, this.durationSeconds)) * 100)
  }

  getTimeRemaining(elapsedSeconds = 0) {
    return Math.max(0, this.startTimeSeconds + this.durationSeconds - elapsedSeconds)
  }

  isActive(elapsedSeconds = 0) {
    return this.status === 'ACTIVE' && elapsedSeconds >= this.startTimeSeconds && this.getTimeRemaining(elapsedSeconds) > 0
  }
}

export class SimulationSnapshot {
  constructor(data = {}) {
    this.tick = numberOr(data.tick ?? data.currentTick, 0)
    this.elapsedSeconds = numberOr(data.elapsedSeconds, this.tick)
    this.vehicles = (data.vehicles || []).map((vehicle) =>
      vehicle instanceof Vehicle ? vehicle : new Vehicle(vehicle)
    )
    this.trafficEvents = (data.trafficEvents || data.events || []).map((event) =>
      event instanceof TrafficEvent ? event : new TrafficEvent(event)
    )
    this.trafficSignals = Array.isArray(data.trafficSignals) ? data.trafficSignals : []
  }

  getActiveEvents() {
    return this.trafficEvents.filter((event) => event.isActive(this.elapsedSeconds))
  }

  getMovingVehicles() {
    return this.vehicles.filter((vehicle) => vehicle.status === 'MOVING' || vehicle.status === 'RUNNING')
  }

  getVehicleCountByType() {
    return this.vehicles.reduce((counts, vehicle) => {
      counts[vehicle.type] = (counts[vehicle.type] || 0) + 1
      return counts
    }, {})
  }
}

export class AlgorithmStatistics {
  constructor(data = {}) {
    this.algorithmName = data.algorithmName || data.name || ''
    this.averageComputationTime = numberOr(data.averageComputationTime, 0)
    this.totalRoutesComputed = numberOr(data.totalRoutesComputed ?? data.routeRecalculationCount, 0)
    this.averagePathLength = numberOr(data.averagePathLength ?? data.averageRouteCost, 0)
    this.routeRecalculationCount = numberOr(data.routeRecalculationCount, 0)
    this.averageRouteCost = numberOr(data.averageRouteCost, this.averagePathLength)
    this.successfulRoutes = numberOr(data.successfulRoutes, this.totalRoutesComputed)
    this.failedRoutes = numberOr(data.failedRoutes, 0)
  }

  getEfficiencyScore() {
    const timeScore = 100 / (1 + this.averageComputationTime)
    const pathScore = 100 / (1 + this.averagePathLength)
    return (timeScore + pathScore) / 2
  }

  getSuccessRate() {
    const total = this.successfulRoutes + this.failedRoutes
    return total ? (this.successfulRoutes / total) * 100 : 0
  }
}

export class TrafficStatistics {
  constructor(data = {}) {
    this.totalVehicles = numberOr(data.totalVehicles, 0)
    this.arrivedVehicles = numberOr(data.arrivedVehicles, 0)
    this.averageTravelTime = numberOr(data.averageTravelTime, 0)
    this.averageCongestionLevel = numberOr(data.averageCongestionLevel ?? data.averageCongestion, 0)
    this.blockedRoadCount = numberOr(data.blockedRoadCount ?? data.closedRoads, 0)
  }

  getAverageCongestionPercentage() {
    return this.averageCongestionLevel * 100
  }

  getSuccessRate() {
    return this.totalVehicles ? (this.arrivedVehicles / this.totalVehicles) * 100 : 0
  }
}

export function createGridNetwork(rows = 5, cols = 5) {
  const intersections = []
  const roads = []
  const spacing = 110

  for (let row = 0; row < rows; row += 1) {
    for (let col = 0; col < cols; col += 1) {
      intersections.push(
        new Intersection({
          id: `I-${row}-${col}`,
          name: `Intersection ${row + 1},${col + 1}`,
          x: 70 + col * spacing,
          y: 70 + row * spacing,
        })
      )
    }
  }

  const idFor = (row, col) => `I-${row}-${col}`
  for (let row = 0; row < rows; row += 1) {
    for (let col = 0; col < cols; col += 1) {
      if (col < cols - 1) {
        roads.push(
          new Road({
            id: `R-${row}-${col}-E`,
            startIntersectionId: idFor(row, col),
            endIntersectionId: idFor(row, col + 1),
            distance: 1,
          })
        )
      }
      if (row < rows - 1) {
        roads.push(
          new Road({
            id: `R-${row}-${col}-S`,
            startIntersectionId: idFor(row, col),
            endIntersectionId: idFor(row + 1, col),
            distance: 1,
          })
        )
      }
    }
  }

  return new RoadNetwork({ intersections, roads })
}
