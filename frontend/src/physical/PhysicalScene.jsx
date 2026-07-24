import { useEffect, useRef, useState } from 'react'
import { Application, Container, Graphics } from 'pixi.js'
import { simulationApi } from '../api/trafficAPI'
import { fitTransform } from './sceneTransform'
import { interpolateVehicles } from './interpolate'
import { drawStatic, drawVehicles } from './layers/drawScene'

// The lane-level renderer. It owns one PixiJS application, polls the physical
// scene around a point, and animates vehicles between the frames it receives.
//
// Two clocks are avoided by never fetching a frame of its own choosing: it asks
// the physical-view endpoint, which serves whatever frame is current, and shows
// exactly that. Interpolation only smooths pixels between two of those frames;
// it never invents authoritative state.

const POLL_INTERVAL_MS = 600
const BACKGROUND = 0x161a20

export default function PhysicalScene({ target, onClose }) {
  const mountRef = useRef(null)
  const stateRef = useRef({
    app: null, staticLayer: null, vehicleLayer: null,
    previous: null, current: null, fetchedAt: 0, radius: target?.radiusMetres ?? 150,
    view: { zoom: 1, panX: 0, panY: 0 },
  })
  const [status, setStatus] = useState('Loading scene…')
  const [vehicleCount, setVehicleCount] = useState(0)

  useEffect(() => {
    let cancelled = false
    let poll = null
    const store = stateRef.current
    const mount = mountRef.current

    async function boot() {
      const app = new Application()
      await app.init({ background: BACKGROUND, antialias: true, resizeTo: mount, autoDensity: true })
      if (cancelled) { app.destroy(true); return }
      store.app = app
      mount.appendChild(app.canvas)

      const staticGraphics = new Graphics()
      const vehicleGraphics = new Graphics()
      const world = new Container()
      world.addChild(staticGraphics, vehicleGraphics)
      app.stage.addChild(world)
      store.staticLayer = staticGraphics
      store.vehicleLayer = vehicleGraphics

      app.ticker.add(() => render(app, store))

      await fetchScene(store, target, setStatus, () => cancelled)
      poll = window.setInterval(
        () => fetchScene(store, target, setStatus, () => cancelled), POLL_INTERVAL_MS,
      )
    }

    boot()
    return () => {
      cancelled = true
      if (poll) window.clearInterval(poll)
      if (store.app) { store.app.destroy(true, { children: true }); store.app = null }
    }
  }, [target])

  function render(app, store) {
    const now = performance.now()
    const transform = fitTransform(store.radius, app.screen.width, app.screen.height, store.view)
    if (store.dirtyStatic && store.current) {
      drawStatic(store.staticLayer, store.current, transform)
      store.dirtyStatic = false
      store.lastTransform = transform
    }
    // Redraw static too when the canvas size changed, so it tracks a resize.
    if (store.lastTransform && store.lastTransform.scale !== transform.scale) {
      drawStatic(store.staticLayer, store.current, transform)
      store.lastTransform = transform
    }
    const t = store.current ? (now - store.fetchedAt) / POLL_INTERVAL_MS : 0
    const vehicles = interpolateVehicles(
      store.previous?.vehicles, store.current?.vehicles, t,
    )
    drawVehicles(store.vehicleLayer, vehicles, transform)
    if (vehicles.length !== store.shownCount) {
      store.shownCount = vehicles.length
      setVehicleCount(vehicles.length)
    }
  }

  const onWheel = (event) => {
    event.preventDefault()
    const store = stateRef.current
    const factor = event.deltaY < 0 ? 1.1 : 1 / 1.1
    store.view = { ...store.view, zoom: Math.max(0.4, Math.min(6, store.view.zoom * factor)) }
    store.lastTransform = null
  }

  return (
    <div className="physical-overlay" role="dialog" aria-label="Physical lane view">
      <div className="physical-toolbar">
        <div>
          <strong>Physical view</strong>
          <span className="physical-note">{status} · {vehicleCount} vehicles · scroll to zoom</span>
        </div>
        <button type="button" onClick={onClose} className="physical-close">Close</button>
      </div>
      <div className="physical-canvas" ref={mountRef} onWheel={onWheel} />
    </div>
  )
}

async function fetchScene(store, target, setStatus, isCancelled) {
  if (!target) return
  try {
    const scene = await simulationApi.physicalView(
      target.latitude, target.longitude, target.radiusMetres,
    )
    if (isCancelled()) return
    store.previous = store.current
    store.current = scene
    store.radius = scene.radiusMetres
    store.fetchedAt = performance.now()
    store.dirtyStatic = true
    setStatus(scene.frameId >= 0 ? `Live · frame ${scene.frameId}` : 'Live · geometry only')
  } catch (error) {
    if (!isCancelled()) setStatus(`Could not load scene: ${error.message}`)
  }
}
