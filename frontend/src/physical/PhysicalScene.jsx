import { useEffect, useRef, useState } from 'react'
import { Application, Container, Graphics } from 'pixi.js'
import { simulationApi } from '../api/trafficAPI'
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

// The lane-level renderer.
//
// Everything is drawn once in world metres into a container; zoom and pan are a
// scale and a translation on that container. Pixi applies them to every layer
// at once, so geometry and vehicles cannot end up at different scales — the
// defect that made zooming feel like the road and the traffic were racing each
// other — and zooming costs no redraw.
//
// The view is not tied to the patch it opened on. Dragging moves the camera,
// and when it has travelled far enough from the loaded centre the scene is
// refetched around where the user is now looking, so the whole map is
// reachable without closing and reopening.

const POLL_INTERVAL_MS = 600
const BACKGROUND = 0xe7e3da
// Refetch once the camera has moved this share of the loaded radius.
const REFETCH_AT_FRACTION = 0.35
const MIN_RADIUS_METRES = 90
const MAX_RADIUS_METRES = 1000
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
    origin: null,
    viewport: {
      latitude: Number(viewport.latitude),
      longitude: Number(viewport.longitude),
    },
    radius: Number(viewport.radiusMetres) || 220,
    camera: { x: 0, y: 0, zoom: clampMapZoom(viewport.zoom) },
    fetching: false,
    drag: null,
    inertia: { x: 0, y: 0 },
    lastTickAt: 0,
    onViewportChange: null,
  }
}

function currentViewport(store) {
  const origin = store.origin ?? store.viewport
  const centre = offsetToLatLon(origin, store.camera.x, store.camera.y)
  return {
    ...centre,
    zoom: store.camera.zoom,
    radiusMetres: store.radius,
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

export default function PhysicalScene({ viewport, onViewportChange, onClose }) {
  const mountRef = useRef(null)
  const storeRef = useRef(null)
  const initialViewportRef = useRef(viewport)
  const onViewportChangeRef = useRef(onViewportChange)
  const [status, setStatus] = useState('Loading scene…')
  const [counts, setCounts] = useState({ vehicles: 0, buildings: 0, assets: 0 })

  useEffect(() => {
    onViewportChangeRef.current = onViewportChange
  }, [onViewportChange])

  useEffect(() => {
    let cancelled = false
    let poll = null
    const mount = mountRef.current
    const store = createStore(initialViewportRef.current)
    store.onViewportChange = (next) => onViewportChangeRef.current?.(next)
    storeRef.current = store

    async function boot() {
      const app = new Application()
      await app.init({ background: BACKGROUND, antialias: true, resizeTo: mount, autoDensity: true })
      if (cancelled) { app.destroy(true); return }
      store.app = app
      mount.appendChild(app.canvas)

      // Whatever artwork is present; every missing slot falls back to a drawn
      // shape, so an empty assets folder is a supported state rather than a fault.
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

      await load(store, setStatus, () => cancelled)
      poll = window.setInterval(() => load(store, setStatus, () => cancelled), POLL_INTERVAL_MS)
    }

    boot()
    return () => {
      cancelled = true
      if (poll) window.clearInterval(poll)
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
    if (store) reportViewport(store)
    onClose()
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
            {status} · {counts.vehicles} vehicles · {counts.buildings} buildings · {counts.assets} assets · drag · wheel or double-click to zoom
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

  if (store.dirty && store.current) {
    drawStatic(layers.static, store.current, store.assets)
    drawSignals(layers.signals, store.current)
    store.dirty = false
  }

  // Reported every tick from whatever scene is current, rather than only when
  // the geometry is redrawn, so the figures cannot silently stick at zero if a
  // redraw is skipped.
  const vehicles = store.current?.vehicles?.length ?? 0
  const buildings = store.current?.buildings?.length ?? 0
  if (vehicles !== store.shownVehicles || buildings !== store.shownBuildings) {
    store.shownVehicles = vehicles
    store.shownBuildings = buildings
    setCounts({ vehicles, buildings, assets: assetCount(store.assets) })
  }

  const elapsed = store.current ? (performance.now() - store.fetchedAt) / POLL_INTERVAL_MS : 0
  const drawn = interpolateVehicles(store.previous?.vehicles, store.current?.vehicles, elapsed)
  drawTrails(layers.trails, drawn)
  layers.vehicles.update(drawn)
}

/**
 * Fetches the scene the camera is looking at, re-centring the request when the
 * camera has wandered far enough from the last one.
 */
async function load(store, setStatus, isCancelled) {
  if (store.fetching) return
  store.fetching = true
  try {
    let request = {
      ...(store.origin ?? store.viewport),
      radiusMetres: store.radius,
    }
    const travelled = Math.hypot(store.camera.x, store.camera.y)
    let recenterOffset = null
    if (store.origin && travelled > store.radius * REFETCH_AT_FRACTION) {
      const moved = offsetToLatLon(store.origin, store.camera.x, store.camera.y)
      request = { ...moved, radiusMetres: store.radius }
      recenterOffset = { x: store.camera.x, y: store.camera.y }
    }
    // Ask for as much map as is actually on screen, with a margin so panning
    // does not stutter at the edge. This only decides how much is fetched; the
    // scale it is drawn at is fixed by the camera alone.
    if (store.app) {
      const scale = screenScale(store)
      const visible = Math.hypot(store.app.screen.width, store.app.screen.height) / 2 / scale
      const wanted = Math.max(MIN_RADIUS_METRES,
        Math.min(MAX_RADIUS_METRES, Math.round(visible * 1.3)))
      if (Math.abs(wanted - store.radius) > store.radius * 0.25) {
        request.radiusMetres = wanted
      }
    }

    const scene = await simulationApi.physicalView(
      request.latitude, request.longitude, request.radiusMetres,
    )
    if (isCancelled()) return
    store.previous = store.current
    store.current = scene
    store.origin = scene.origin
    store.viewport = {
      latitude: scene.origin.latitude,
      longitude: scene.origin.longitude,
    }
    store.radius = scene.radiusMetres
    if (recenterOffset) {
      // Camera offsets are relative to the scene origin. Once the server has
      // accepted the requested centre as its new origin, remove only the offset
      // included in that request. Any movement made while the request was in
      // flight remains, so a slow response cannot snap the map backwards.
      store.camera.x -= recenterOffset.x
      store.camera.y -= recenterOffset.y
      reportViewport(store)
    }
    store.fetchedAt = performance.now()
    store.dirty = true
    setStatus(scene.frameId >= 0 ? `Live · frame ${scene.frameId}` : 'Live · geometry only')
  } catch (error) {
    if (!isCancelled()) setStatus(`Could not load scene: ${error.message}`)
  } finally {
    store.fetching = false
  }
}
