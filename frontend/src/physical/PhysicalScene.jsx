import { useEffect, useRef, useState } from 'react'
import { Application, Container, Graphics } from 'pixi.js'
import { simulationApi } from '../api/trafficAPI'
import { fitScale, offsetToLatLon } from './sceneTransform'
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
// How much map a wheel notch is worth. Scroll deltas differ wildly between a
// mouse and a trackpad, so the step is clamped and applied exponentially rather
// than multiplied per event, which is what made one flick jump several levels.
const ZOOM_PER_DELTA = 0.0012
const MAX_DELTA_PER_EVENT = 120
// The on-screen scale is anchored to this, never to the radius that happens to
// be loaded. Tying it to the fetched radius meant zooming out shrank the view
// twice: once through the camera, and again as the wider request fed back in.
const REFERENCE_RADIUS_METRES = 220

function screenScale(store, app) {
  return fitScale(REFERENCE_RADIUS_METRES, app.screen.width, app.screen.height) * store.camera.zoom
}

export default function PhysicalScene({ target, onClose }) {
  const mountRef = useRef(null)
  const store = useRef({
    app: null, world: null, layers: null,
    previous: null, current: null, fetchedAt: 0,
    origin: null, radius: target?.radiusMetres ?? 220,
    camera: { x: 0, y: 0, zoom: 1 },
    fetching: false, drag: null,
  }).current
  const [status, setStatus] = useState('Loading scene…')
  const [counts, setCounts] = useState({ vehicles: 0, buildings: 0, assets: 0 })

  useEffect(() => {
    let cancelled = false
    let poll = null
    const mount = mountRef.current

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

      await load(store, target, setStatus, () => cancelled)
      poll = window.setInterval(() => load(store, target, setStatus, () => cancelled), POLL_INTERVAL_MS)
    }

    boot()
    return () => {
      cancelled = true
      if (poll) window.clearInterval(poll)
      if (store.app) { store.app.destroy(true, { children: true }); store.app = null }
    }
  }, [target, store])

  const onWheel = (event) => {
    event.preventDefault()
    const step = Math.max(-MAX_DELTA_PER_EVENT, Math.min(MAX_DELTA_PER_EVENT, event.deltaY))
    const factor = Math.exp(-step * ZOOM_PER_DELTA)
    store.camera.zoom = Math.max(0.2, Math.min(10, store.camera.zoom * factor))
  }

  const onPointerDown = (event) => {
    store.drag = { x: event.clientX, y: event.clientY }
    event.currentTarget.setPointerCapture?.(event.pointerId)
  }

  const onPointerMove = (event) => {
    if (!store.drag || !store.app) return
    const scale = screenScale(store, store.app)
    store.camera.x -= (event.clientX - store.drag.x) / scale
    store.camera.y += (event.clientY - store.drag.y) / scale
    store.drag = { x: event.clientX, y: event.clientY }
  }

  const onPointerUp = () => { store.drag = null }

  return (
    <div className="physical-overlay" role="dialog" aria-label="Physical lane view">
      <div className="physical-toolbar">
        <div>
          <strong>Physical view</strong>
          <span className="physical-note">
            {status} · {counts.vehicles} vehicles · {counts.buildings} buildings · {counts.assets} assets · drag to move, scroll to zoom
          </span>
        </div>
        <button type="button" onClick={onClose} className="physical-close">Close</button>
      </div>
      <div
        className="physical-canvas"
        ref={mountRef}
        onWheel={onWheel}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerLeave={onPointerUp}
      />
    </div>
  )
}

/** One animation frame: apply the camera, redraw what changed, interpolate vehicles. */
function tick(store, setCounts) {
  const { app, world, layers } = store
  if (!app || !world || !layers) return

  const scale = screenScale(store, app)
  world.scale.set(scale)
  world.position.set(
    app.screen.width / 2 - store.camera.x * scale,
    app.screen.height / 2 - store.camera.y * scale,
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
async function load(store, target, setStatus, isCancelled) {
  if (!target || store.fetching) return
  store.fetching = true
  try {
    let request = { ...target, radiusMetres: store.radius }
    const travelled = Math.hypot(store.camera.x, store.camera.y)
    if (store.origin && travelled > store.radius * REFETCH_AT_FRACTION) {
      const moved = offsetToLatLon(store.origin, store.camera.x, store.camera.y)
      request = { ...moved, radiusMetres: store.radius }
      // The camera is expressed relative to the scene origin, so re-centring the
      // request means re-zeroing it, or the view would jump by what it just moved.
      store.camera.x = 0
      store.camera.y = 0
      Object.assign(target, moved)
    }
    // Ask for as much map as is actually on screen, with a margin so panning
    // does not stutter at the edge. This only decides how much is fetched; the
    // scale it is drawn at is fixed by the camera alone.
    if (store.app) {
      const scale = screenScale(store, store.app)
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
    store.radius = scene.radiusMetres
    store.fetchedAt = performance.now()
    store.dirty = true
    setStatus(scene.frameId >= 0 ? `Live · frame ${scene.frameId}` : 'Live · geometry only')
  } catch (error) {
    if (!isCancelled()) setStatus(`Could not load scene: ${error.message}`)
  } finally {
    store.fetching = false
  }
}
