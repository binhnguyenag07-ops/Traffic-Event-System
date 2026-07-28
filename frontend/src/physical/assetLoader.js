// Loads whatever artwork is present and shrugs at the rest.
//
// A missing file is the normal case, not an error: the manifest describes every
// slot the renderer *could* use, and the repository ships none of them. So each
// load is attempted independently and a failure resolves to null rather than
// rejecting, which keeps one absent PNG from taking the whole scene down.

import { Assets, Matrix } from 'pixi.js'
import { allAssetSlots } from './assetManifest'

const ASSET_LOAD_TIMEOUT_MS = 3000

/**
 * How much world space one texture tile covers.
 *
 * One six-metre repeat for every category made roof tiles, parks and the base
 * ground compete with lane markings. Each family now uses the scale at which
 * it is meant to be read: road grain is compact, roof patterns span a normal
 * building, and land cover changes slowly across a block.
 */
const TEXTURE_TILE_METRES = {
  surfaces: 12,
  buildings: 24,
  land: 42,
}

/** A fill style that repeats the texture at a sensible ground scale. */
function tiledFill(texture, group) {
  const size = texture.width || texture.source?.width || 1024
  const scale = TEXTURE_TILE_METRES[group] / size
  return { texture, matrix: new Matrix().scale(scale, scale) }
}

/**
 * Attempts every slot in the manifest.
 *
 * Vehicles come back as raw textures, because they become sprites. Surfaces and
 * building roofs come back as ready-made fill styles, complete with the matrix
 * that tiles them at ground scale — that keeps the drawing layer free of any
 * PixiJS import, so it stays testable without a renderer.
 *
 * Anything absent is simply missing from the result, and the renderer draws its
 * own shape instead.
 */
function loadWithTimeout(url, loadAsset, timeoutMs) {
  let timeout
  return Promise.race([
    loadAsset(url),
    new Promise((_, reject) => {
      timeout = globalThis.setTimeout(
        () => reject(new Error(`Asset timed out: ${url}`)),
        timeoutMs,
      )
    }),
  ]).finally(() => globalThis.clearTimeout(timeout))
}

export async function loadSceneAssets({
  loadAsset = (url) => Assets.load(url),
  timeoutMs = ASSET_LOAD_TIMEOUT_MS,
} = {}) {
  const loaded = { vehicles: {}, surfaces: {}, buildings: {}, land: {} }
  await Promise.all(allAssetSlots().map(async ({ group, key, url }) => {
    try {
      const texture = await loadWithTimeout(url, loadAsset, timeoutMs)
      if (!texture) return
      loaded[group][key] = group === 'vehicles' ? texture : tiledFill(texture, group)
    } catch {
      // Missing, failed, or stalled slots all use the fallback drawing. A
      // broken texture must never hold the whole Physical View in "Loading".
    }
  }))
  return loaded
}

/** How many slots are filled, for the status line. */
export function assetCount(assets) {
  if (!assets) return 0
  return Object.values(assets)
    .reduce((total, group) => total + Object.keys(group).length, 0)
}
