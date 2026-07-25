// Loads whatever artwork is present and shrugs at the rest.
//
// A missing file is the normal case, not an error: the manifest describes every
// slot the renderer *could* use, and the repository ships none of them. So each
// load is attempted independently and a failure resolves to null rather than
// rejecting, which keeps one absent PNG from taking the whole scene down.

import { Assets } from 'pixi.js'
import { allAssetSlots } from './assetManifest'

/**
 * Attempts every slot in the manifest.
 *
 * Returns `{ vehicles, surfaces, buildings }`, each an object of key → texture
 * for the files that loaded. Anything absent is simply missing from the result,
 * and the renderer draws its own shape instead.
 */
export async function loadSceneAssets() {
  const loaded = { vehicles: {}, surfaces: {}, buildings: {} }
  await Promise.all(allAssetSlots().map(async ({ group, key, url }) => {
    try {
      const texture = await Assets.load(url)
      if (texture) loaded[group][key] = texture
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
