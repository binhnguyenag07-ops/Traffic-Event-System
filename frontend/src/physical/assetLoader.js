// Loads whatever artwork is present and shrugs at the rest.
//
// A missing file is the normal case, not an error: the manifest describes every
// slot the renderer *could* use, and the repository ships none of them. So each
// load is attempted independently and a failure resolves to null rather than
// rejecting, which keeps one absent PNG from taking the whole scene down.

import { Assets, Matrix } from 'pixi.js'
import { allAssetSlots } from './assetManifest'

/**
 * How much ground one tile of a surface texture covers, in metres.
 *
 * Without this a 1024-pixel texture would be mapped one pixel to one metre and
 * a single tile would span a kilometre, which reads as a flat smear rather than
 * as tarmac. Six metres is about two lane widths, so the grain stays visible at
 * the zoom the view is usually at.
 */
const TEXTURE_TILE_METRES = 6

/** A fill style that repeats the texture at a sensible ground scale. */
function tiledFill(texture) {
  const size = texture.width || texture.source?.width || 1024
  const scale = TEXTURE_TILE_METRES / size
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
export async function loadSceneAssets() {
  const loaded = { vehicles: {}, surfaces: {}, buildings: {} }
  await Promise.all(allAssetSlots().map(async ({ group, key, url }) => {
    try {
      const texture = await Assets.load(url)
      if (!texture) return
      loaded[group][key] = group === 'vehicles' ? texture : tiledFill(texture)
    } catch {
      // Slot not filled yet. The fallback drawing covers it.
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
