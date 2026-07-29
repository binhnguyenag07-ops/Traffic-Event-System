import { useEffect, useRef, useState } from 'react'
import { Application, Container, Graphics } from 'pixi.js'
import { simulationApi } from '../api/trafficAPI'
import { formatTimeOfDay, timeOfDaySeconds } from '../simulationTime'
import { physicalCameraPresentation } from './runtimeStatus'
import {
  cameraAfterZoom,
  clampMapZoom,
  pixelsPerMetreAtZoom,
} from '../mapViewport'
import { offsetToLatLon } from './sceneTransform'
import { interpolateVehicles } from './interpolate'
import { drawSignals, drawStatic, drawTrails } from './layers/drawScene'
import { VehicleLayer, buildVehicleTextures } from './vehicleSprites'
import { assetCount, loadSceneAssets } from './assetLoader'
import {
  physicalTimelineChanged,
  rebasePhysicalState,
  shouldApplyViewportState,
  viewportStreamRadius,
  visibleViewportVehicles,
} from './viewportStream'
import {
  nextPollCadence,
  PHYSICAL_POLL_INTERVAL_MS,
} from './pollCadence'

// The lane-level renderer.
//
// Everything is drawn once in world metres into a container; zoom and pan are a
// scale and a translation on that container. Pixi applies them to every layer
// at once, so geometry and vehicles cannot end up at different scales — the
// defect that made zooming feel like the road and the traffic were racing each
// other — and zooming costs no redraw.
//
// The complete static map is loaded once. Polls carry only vehicles and signals
// in the current camera plus a small prefetch ring; their metre coordinates are
// rebased onto the retained origin. Panning therefore cannot make buildings pop
// in and out, while a dense district-wide fleet is never sent to Pixi at once.

const MAX_RENDER_FPS = 60
const BACKGROUND = 0xe7e3da
const WHEEL_PIXELS_PER_ZOOM_LEVEL = 240
const MAX_WHEEL_DELTA_PIXELS = 240
const INERTIA_DECAY_MS = 220
const INERTIA_STOP_METRES_PER_MS = 0.002
const KEYBOARD_PAN_PIXELS = 80

function screenScale(store) {
  const latitude = store.origin?.latitude ?? store.viewport.latitude
  return pixelsPerMetreAtZoom(latitude, store.camera.zoom)
}

function createStore(viewport) {
  return {
    app: null,
    world: null,
    layers: null,
    previous: null,
    current: null,
    fetchedAt: 0,
    animationDurationMs: 1000,
    origin: null,
    viewport: {
      latitude: Number(viewport.latitude),
      longitude: Number(viewport.longitude),
    },
    viewRadius: Number(viewport.radiusMetres) || 220,
    camera: { x: 0, y: 0, zoom: clampMapZoom(viewport.zoom) },
    fetching: false,
    needsGeometry: true,
    staticDirty: false,
    signalsDirty: false,
    drag: null,
    inertia: { x: 0, y: 0 },
    lastTickAt: 0,
    lastStateQuery: null,
    onLiveFrame: null,
    onViewportChange: null,
  }
}

function currentViewport(store) {
  const origin = store.origin ?? store.viewport
  const centre = offsetToLatLon(origin, store.camera.x, store.camera.y)
  return {
    ...centre,
    zoom: store.camera.zoom,
    radiusMetres: store.viewRadius,
  }
}

function reportViewport(store) {
  store.onViewportChange?.(currentViewport(store))
}

function changeZoom(store, zoomDelta, screenX = 0, screenY = 0) {
  const nextZoom = clampMapZoom(store.camera.zoom + zoomDelta)
  if (Math.abs(nextZoom - store.camera.zoom) < 1e-6) return
  const oldScale = screenScale(store)
  const latitude = currentViewport(store).latitude
  const nextScale = pixelsPerMetreAtZoom(latitude, nextZoom)
  const moved = cameraAfterZoom(store.camera, screenX, screenY, oldScale, nextScale)
  store.camera.x = moved.x
  store.camera.y = moved.y
  store.camera.zoom = nextZoom
  store.inertia = { x: 0, y: 0 }
  reportViewport(store)
}

function panByPixels(store, screenX, screenY) {
  const scale = screenScale(store)
  store.camera.x += screenX / scale
  store.camera.y -= screenY / scale
  store.inertia = { x: 0, y: 0 }
  reportViewport(store)
}

export default function PhysicalScene({
  viewport,
  clockLabel,
  startTimeOfDaySeconds = 0,
  simulationStatus = 'READY',
  simulationSecondsPerTick = 0,
  playbackIntervalMs = 1000,
  statusConnected = true,
  onViewportChange,
  onClose,
}) {
  const mountRef = useRef(null)
  const storeRef = useRef(null)
  const initialViewportRef = useRef(viewport)
  const onViewportChangeRef = useRef(onViewportChange)
  const startTimeOfDayRef = useRef(startTimeOfDaySeconds)
  const [status, setStatus] = useState('Loading scene…')
  const [liveClockLabel, setLiveClockLabel] = useState(clockLabel || 'current time')
  const [counts, setCounts] = useState({ vehicles: 0, buildings: 0, assets: 0 })
  const camera = physicalCameraPresentation(
    simulationStatus,
    simulationSecondsPerTick,
    playbackIntervalMs,
    statusConnected,
  )

  useEffect(() => {
    onViewportChangeRef.current = onViewportChange
  }, [onViewportChange])

  useEffect(() => {
    startTimeOfDayRef.current = startTimeOfDaySeconds
    const frame = storeRef.current?.current
    if (!frame) {
      if (clockLabel) setLiveClockLabel(clockLabel)
      return
    }
    const nextClock = formatTimeOfDay(timeOfDaySeconds(frame, startTimeOfDaySeconds))
    setLiveClockLabel((current) => current === nextClock ? current : nextClock)
  }, [clockLabel, startTimeOfDaySeconds])

  useEffect(() => {
    let cancelled = false
    let poll = null
    const mount = mountRef.current
    const store = createStore(initialViewportRef.current)
    store.onViewportChange = (next) => onViewportChangeRef.current?.(next)
    store.onLiveFrame = (frame) => {
      const nextClock = formatTimeOfDay(timeOfDaySeconds(frame, startTimeOfDayRef.current))
      setLiveClockLabel((current) => current === nextClock ? current : nextClock)
    }
    storeRef.current = store

    async function boot() {
      const app = new Application()
      await app.init({ background: BACKGROUND, antialias: true, resizeTo: mount, autoDensity: true })
      if (cancelled) { app.destroy(true); return }
      // Pixi otherwise follows a 120/144 Hz display and repeats the complete
      // full-map render that many times even though state only arrives at 4 Hz.
      app.ticker.maxFPS = MAX_RENDER_FPS
      store.app = app
      mount.appendChild(app.canvas)

      // Geometry and optional artwork are independent, so start both now. A
      // slow texture must not postpone the comparatively small API request.
      const geometry = loadGeometry(store, setStatus, () => cancelled)
      // Whatever artwork is present; every missing, failed, or timed-out slot
      // falls back to a drawn shape.
      const assets = await loadSceneAssets()
      if (cancelled) { app.destroy(true); return }
      store.assets = assets

      const world = new Container()
      const layers = {
        static: new Graphics(),
        trails: new Graphics(),
        signals: new Graphics(),
        vehicles: new VehicleLayer(buildVehicleTextures(app.renderer, assets.vehicles)),
      }
      world.addChild(layers.static, layers.trails, layers.signals, layers.vehicles.container)
      app.stage.addChild(world)
      store.world = world
      store.layers = layers

      // A throw in the draw would otherwise leave a blank canvas with no hint of
      // why, which is exactly what a renderer must not do.
      app.ticker.add(() => {
        try {
          tick(store, setCounts)
        } catch (error) {
          if (!store.reportedError) {
            store.reportedError = true
            setStatus(`Render failed: ${error.message}`)
          }
        }
      })

      await geometry
      // The full-map request is geometry-only. Fetch the camera's live subset
      // immediately instead of showing an empty/static frame until the first
      // scheduled poll.
      if (!cancelled && !store.needsGeometry) {
        await loadState(store, setStatus, () => cancelled)
      }

      let cadence = { delayMs: PHYSICAL_POLL_INTERVAL_MS, unchangedForMs: 0 }
      const pollOnce = async () => {
        if (cancelled) return
        const requestStartedAt = performance.now()
        const result = store.needsGeometry
          ? await loadGeometry(store, setStatus, () => cancelled)
          : await loadState(store, setStatus, () => cancelled)
        cadence = nextPollCadence(result, cadence, store.animationDurationMs)
        // Preserve a start-to-start cadence. Scheduling a full 250 ms only
        // after an 11–55 ms request completed skipped authoritative frames at
        // 4× playback, turning one interpolation interval into several road
        // transitions. Idle backoff still uses the same bounded deadlines.
        const requestElapsed = performance.now() - requestStartedAt
        const wait = Math.max(0, cadence.delayMs - requestElapsed)
        if (!cancelled) poll = window.setTimeout(pollOnce, wait)
      }
      if (!cancelled) poll = window.setTimeout(pollOnce, cadence.delayMs)
    }

    boot()
    return () => {
      cancelled = true
      if (poll) window.clearTimeout(poll)
      if (store.app) { store.app.destroy(true, { children: true }); store.app = null }
      if (storeRef.current === store) storeRef.current = null
    }
  }, [])

  const onWheel = (event) => {
    const store = storeRef.current
    if (!store) return
    event.preventDefault()
    const rect = event.currentTarget.getBoundingClientRect()
    const unit = event.deltaMode === 1 ? 16 : event.deltaMode === 2 ? rect.height : 1
    const pixels = Math.max(-MAX_WHEEL_DELTA_PIXELS,
      Math.min(MAX_WHEEL_DELTA_PIXELS, event.deltaY * unit))
    changeZoom(
      store,
      -pixels / WHEEL_PIXELS_PER_ZOOM_LEVEL,
      event.clientX - rect.left - rect.width / 2,
      event.clientY - rect.top - rect.height / 2,
    )
  }

  const onPointerDown = (event) => {
    const store = storeRef.current
    if (!store) return
    if (event.pointerType === 'mouse' && event.button !== 0) return
    event.currentTarget.focus({ preventScroll: true })
    store.inertia = { x: 0, y: 0 }
    store.drag = {
      x: event.clientX,
      y: event.clientY,
      at: performance.now(),
      pointerId: event.pointerId,
    }
    event.currentTarget.classList.add('dragging')
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  const onPointerMove = (event) => {
    const store = storeRef.current
    if (!store) return
    if (!store.drag || store.drag.pointerId !== event.pointerId || !store.app) return
    const now = performance.now()
    const elapsed = Math.max(1, now - store.drag.at)
    const scale = screenScale(store)
    const moveEast = -(event.clientX - store.drag.x) / scale
    const moveNorth = (event.clientY - store.drag.y) / scale
    store.camera.x += moveEast
    store.camera.y += moveNorth
    // Blend samples so one noisy pointer event cannot launch the map.
    store.inertia.x = store.inertia.x * 0.35 + (moveEast / elapsed) * 0.65
    store.inertia.y = store.inertia.y * 0.35 + (moveNorth / elapsed) * 0.65
    store.drag = {
      x: event.clientX,
      y: event.clientY,
      at: now,
      pointerId: event.pointerId,
    }
  }

  const onPointerUp = (event) => {
    const store = storeRef.current
    if (!store) return
    if (store.drag?.pointerId !== event.pointerId) return
    store.drag = null
    event.currentTarget.classList.remove('dragging')
    if (event.currentTarget.hasPointerCapture?.(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId)
    }
    reportViewport(store)
  }

  const onDoubleClick = (event) => {
    const store = storeRef.current
    if (!store) return
    event.preventDefault()
    const rect = event.currentTarget.getBoundingClientRect()
    changeZoom(
      store,
      1,
      event.clientX - rect.left - rect.width / 2,
      event.clientY - rect.top - rect.height / 2,
    )
  }

  const close = () => {
    const store = storeRef.current
    const finalViewport = store ? currentViewport(store) : viewport
    onClose(finalViewport)
  }

  const onKeyDown = (event) => {
    const store = storeRef.current
    if (!store) return
    if (event.key === 'Escape') {
      close()
      return
    }
    if (event.key === '+' || event.key === '=') {
      event.preventDefault()
      changeZoom(store, 1)
    } else if (event.key === '-' || event.key === '_') {
      event.preventDefault()
      changeZoom(store, -1)
    } else if (event.key === 'ArrowLeft') {
      event.preventDefault()
      panByPixels(store, -KEYBOARD_PAN_PIXELS, 0)
    } else if (event.key === 'ArrowRight') {
      event.preventDefault()
      panByPixels(store, KEYBOARD_PAN_PIXELS, 0)
    } else if (event.key === 'ArrowUp') {
      event.preventDefault()
      panByPixels(store, 0, -KEYBOARD_PAN_PIXELS)
    } else if (event.key === 'ArrowDown') {
      event.preventDefault()
      panByPixels(store, 0, KEYBOARD_PAN_PIXELS)
    }
  }

  const zoomBy = (delta) => {
    const store = storeRef.current
    if (store) changeZoom(store, delta)
  }

  return (
    <div className="physical-overlay" role="dialog" aria-label="Physical lane view">
      <div className="physical-toolbar">
        <div>
          <strong>Physical view</strong>
          <span className="physical-note">
            {camera.cameraLabel} · {liveClockLabel} · {camera.paceLabel} · {status} · {counts.vehicles} visible vehicles · {counts.buildings} buildings · {counts.assets} assets · drag · wheel or double-click to zoom
          </span>
        </div>
        <button type="button" onClick={close} className="physical-close">Close</button>
      </div>
      <div className="physical-map">
        <div
          className="physical-canvas"
          ref={mountRef}
          role="application"
          aria-label="Interactive physical traffic map"
          tabIndex={0}
          onWheel={onWheel}
          onDoubleClick={onDoubleClick}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
          onKeyDown={onKeyDown}
        />
        <div className="physical-zoom-controls" aria-label="Physical map zoom">
          <button type="button" aria-label="Zoom in" title="Zoom in"
            onClick={() => zoomBy(1)}>+</button>
          <button type="button" aria-label="Zoom out" title="Zoom out"
            onClick={() => zoomBy(-1)}>−</button>
        </div>
      </div>
    </div>
  )
}

/** One animation frame: apply the camera, redraw what changed, interpolate vehicles. */
function tick(store, setCounts) {
  const { app, world, layers } = store
  if (!app || !world || !layers) return

  const now = performance.now()
  const elapsedMs = store.lastTickAt ? Math.min(40, now - store.lastTickAt) : 0
  store.lastTickAt = now
  if (!store.drag && elapsedMs > 0) {
    const speed = Math.hypot(store.inertia.x, store.inertia.y)
    if (speed > INERTIA_STOP_METRES_PER_MS) {
      store.camera.x += store.inertia.x * elapsedMs
      store.camera.y += store.inertia.y * elapsedMs
      const decay = Math.exp(-elapsedMs / INERTIA_DECAY_MS)
      store.inertia.x *= decay
      store.inertia.y *= decay
    } else if (speed > 0) {
      store.inertia = { x: 0, y: 0 }
      reportViewport(store)
    }
  }

  const scale = screenScale(store)
  world.scale.set(scale)
  world.position.set(
    app.screen.width / 2 - store.camera.x * scale,
    app.screen.height / 2 + store.camera.y * scale,
  )

  if (store.staticDirty && store.current) {
    drawStatic(layers.static, store.current, store.assets)
    store.staticDirty = false
  }
  if (store.signalsDirty && store.current) {
    drawSignals(layers.signals, store.current)
    store.signalsDirty = false
  }

  const elapsed = store.current
    ? (performance.now() - store.fetchedAt) / store.animationDurationMs
    : 0
  const drawn = interpolateVehicles(
    store.previous?.vehicles,
    store.current?.vehicles,
    elapsed,
    store.current,
  )
  const visible = visibleViewportVehicles(
    drawn,
    store.camera,
    app.screen.width,
    app.screen.height,
    scale,
  )
  // Counts describe what Pixi is actually submitting, not the district-wide
  // fleet retained by the backend.
  const buildings = store.current?.buildings?.length ?? 0
  if (visible.length !== store.shownVehicles || buildings !== store.shownBuildings) {
    store.shownVehicles = visible.length
    store.shownBuildings = buildings
    setCounts({ vehicles: visible.length, buildings, assets: assetCount(store.assets) })
  }
  drawTrails(layers.trails, visible)
  layers.vehicles.update(visible)
}

/** Loads the complete static network once for the current simulation timeline. */
async function loadGeometry(store, setStatus, isCancelled) {
  if (store.fetching) return 'skipped'
  store.fetching = true
  try {
    const request = store.origin ?? store.viewport
    const scene = await simulationApi.physicalView(
      request.latitude,
      request.longitude,
      store.viewRadius,
      { fullMap: true, includeState: false },
    )
    if (isCancelled()) return
    const scale = screenScale(store)
    const localScene = {
      ...scene,
      vehicles: visibleViewportVehicles(
        scene.vehicles,
        store.camera,
        store.app?.screen.width || 0,
        store.app?.screen.height || 0,
        scale,
      ),
    }
    store.previous = store.current
    store.current = localScene
    store.origin = scene.origin
    store.viewport = {
      latitude: scene.origin.latitude,
      longitude: scene.origin.longitude,
    }
    store.fetchedAt = performance.now()
    store.staticDirty = true
    store.signalsDirty = true
    store.needsGeometry = false
    store.lastStateQuery = null
    setStatus(scene.frameId >= 0
      ? `Full map · frame ${scene.frameId}`
      : 'Full map geometry')
    return 'updated'
  } catch (error) {
    if (!isCancelled()) setStatus(`Could not load scene: ${error.message}`)
    return 'error'
  } finally {
    store.fetching = false
  }
}

/** Polls only moving state and merges it onto the retained static scene. */
async function loadState(store, setStatus, isCancelled) {
  if (store.fetching || !store.origin || !store.current) return 'skipped'
  store.fetching = true
  try {
    const centre = currentViewport(store)
    const radius = viewportStreamRadius(
      store.app?.screen.width || 0,
      store.app?.screen.height || 0,
      screenScale(store),
    )
    const query = {
      latitude: centre.latitude,
      longitude: centre.longitude,
      radiusMetres: radius,
    }
    const state = await simulationApi.physicalViewState(
      centre.latitude, centre.longitude, radius,
    )
    if (isCancelled()) return
    if (physicalTimelineChanged(store.current.timelineId, state.timelineId)) {
      store.needsGeometry = true
      setStatus('Refreshing full map…')
      return 'timeline-changed'
    }
    store.animationDurationMs = Math.max(
      PHYSICAL_POLL_INTERVAL_MS,
      Number(state.playbackIntervalMs) || store.animationDurationMs,
    )
    if (!shouldApplyViewportState(
      store.current.frameId,
      state.frameId,
      store.lastStateQuery,
      query,
    )) {
      return 'unchanged'
    }
    const rebased = rebasePhysicalState(state, store.origin)
    store.previous = store.current
    store.current = {
      ...store.current,
      timelineId: rebased.timelineId,
      frameId: rebased.frameId,
      elapsedSeconds: rebased.elapsedSeconds,
      playbackIntervalMs: rebased.playbackIntervalMs,
      signals: rebased.signals || [],
      vehicles: rebased.vehicles || [],
    }
    store.fetchedAt = performance.now()
    store.signalsDirty = true
    store.lastStateQuery = query
    store.onLiveFrame?.(store.current)
    setStatus(state.frameId >= 0
      ? `Viewport frame ${state.frameId}`
      : 'Viewport geometry')
    return 'updated'
  } catch (error) {
    if (!isCancelled()) setStatus(`Could not update scene: ${error.message}`)
    return 'error'
  } finally {
    store.fetching = false
  }
}
