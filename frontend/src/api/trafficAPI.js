const API_BASE = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })
  if (response.status === 204) return null
  const body = await response.json().catch(() => ({}))
  if (!response.ok) {
    const error = new Error(body.error || body.message || `Request failed (${response.status})`)
    error.status = response.status
    error.body = body
    throw error
  }
  return body
}

const get = (path) => request(path)
const post = (path, body = {}) => request(path, { method: 'POST', body: JSON.stringify(body) })
const put = (path, body = {}) => request(path, { method: 'PUT', body: JSON.stringify(body) })
const remove = (path) => request(path, { method: 'DELETE' })

export const networkApi = {
  getSummary: () => get('/network'),
  getAllIntersections: () => get('/network/intersections'),
  getAllRoads: () => get('/network/roads'),
  getAllBuildings: () => get('/network/buildings'),
  addIntersection: (payload) => post('/network/intersections', payload),
  removeIntersection: (id) => remove(`/network/intersections/${encodeURIComponent(id)}`),
  addRoad: (payload) => post('/network/roads', payload),
  removeRoad: (id) => remove(`/network/roads/${encodeURIComponent(id)}`),
  updateRoadCondition: (id, payload) => put(`/network/roads/${encodeURIComponent(id)}/condition`, payload),
  connectedRoads: (id) => get(`/network/intersections/${encodeURIComponent(id)}/roads`),
  exportNetwork: async () => {
    const response = await fetch(`${API_BASE}/network/export`)
    if (!response.ok) {
      const body = await response.json().catch(() => ({}))
      throw new Error(body.error || `Export failed (${response.status})`)
    }
    return response.blob()
  },
}

export const scenarioApi = {
  list: () => get('/scenarios'),
  // Which scenario the server's network actually came from. Worth asking rather
  // than assuming: activating one replaces the network until something else
  // does, so a reloaded page cannot know from its own state.
  active: () => get('/scenarios/active'),
  activate: (id) => post(`/scenarios/${encodeURIComponent(id)}/activate`),
  importMap: async (file) => {
    const form = new FormData()
    form.append('map', file)
    const response = await fetch(`${API_BASE}/scenarios/import`, { method: 'POST', body: form })
    const body = await response.json().catch(() => ({}))
    if (!response.ok) throw new Error(body.error || `Map import failed (${response.status})`)
    return body
  },
}

export const simulationApi = {
  initialize: (vehicles, events = []) => post('/simulation/initialize', { vehicles, events }),
  start: () => post('/simulation/start'),
  pause: () => post('/simulation/pause'),
  resume: () => post('/simulation/resume'),
  reset: () => post('/simulation/reset'),
  tick: () => post('/simulation/tick'),
  snapshot: () => get('/simulation/snapshot'),
  status: () => get('/simulation/status'),
  demandSchedule: () => get('/simulation/demand/schedule'),
  signals: () => get('/simulation/signals'),
  flowTrace: () => get('/simulation/trace/flow'),
  frameTimeline: () => get('/simulation/trace/frames'),
  frameAt: (elapsedSeconds) => get(
    `/simulation/trace/frames/at?elapsedSeconds=${encodeURIComponent(elapsedSeconds)}`,
  ),
  fundamentalDiagram: () => get('/simulation/trace/fundamental'),
  timeSpaceDiagram: (roadId) => get(`/simulation/trace/roads/${encodeURIComponent(roadId)}/time-space`),
  setParameters: (simulationSecondsPerTick, playbackIntervalMs) =>
    put('/simulation/parameters', { simulationSecondsPerTick, playbackIntervalMs }),
  setPlaybackInterval: (playbackIntervalMs) =>
    put('/simulation/parameters', { playbackIntervalMs }),
  streamUrl: () => `${API_BASE}/simulation/stream`,
  physicalView: (lat, lon, radiusMetres, { fullMap = false } = {}) => get(
    `/simulation/physical-view?lat=${encodeURIComponent(lat)}`
    + `&lon=${encodeURIComponent(lon)}&radiusMetres=${encodeURIComponent(radiusMetres)}`
    + (fullMap ? '&fullMap=true' : ''),
  ),
  physicalViewState: (lat, lon, radiusMetres) => get(
    `/simulation/physical-view/state?lat=${encodeURIComponent(lat)}`
    + `&lon=${encodeURIComponent(lon)}&radiusMetres=${encodeURIComponent(radiusMetres)}`,
  ),
}

export const routingApi = {
  algorithms: () => get('/routing/algorithms'),
  findRoute: (startIntersectionId, destinationIntersectionId, preference, algorithm) =>
    post('/routes', { startIntersectionId, destinationIntersectionId, preference, algorithm }),
  compare: (startIntersectionId, destinationIntersectionId, preference) =>
    post('/routes/compare', { startIntersectionId, destinationIntersectionId, preference }),
}

export const vehicleApi = {
  list: () => get('/vehicles'),
  create: (type, startIntersectionId, destinationIntersectionId) =>
    post('/vehicles', { type, startIntersectionId, destinationIntersectionId }),
  createBetweenBuildings: (type, startBuildingId, destinationBuildingId) =>
    post('/vehicles', { type, startBuildingId, destinationBuildingId }),
  remove: (id) => remove(`/vehicles/${encodeURIComponent(id)}`),
}

export const trafficEventApi = {
  list: () => get('/events'),
  create: (payload) => post('/events', payload),
  updateStatus: (id, status) => put(`/events/${encodeURIComponent(id)}/status`, { status }),
  remove: (id) => remove(`/events/${encodeURIComponent(id)}`),
}

export const statisticsApi = {
  all: () => get('/statistics'),
}

export const experimentApi = {
  latest: () => get('/experiments/latest'),
  run: () => post('/experiments/run'),
}

export { request }
