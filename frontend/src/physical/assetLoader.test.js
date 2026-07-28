import { describe, expect, it } from 'vitest'
import { assetCount, loadSceneAssets } from './assetLoader'
import { allAssetSlots } from './assetManifest'

describe('physical scene asset loading', () => {
  it('loads every available slot independently', async () => {
    const texture = { width: 1024 }
    const loaded = await loadSceneAssets({
      loadAsset: async () => texture,
      timeoutMs: 50,
    })

    expect(assetCount(loaded)).toBe(allAssetSlots().length)
  })

  it('falls back instead of waiting forever for a stalled asset', async () => {
    const loaded = await loadSceneAssets({
      loadAsset: () => new Promise(() => {}),
      timeoutMs: 5,
    })

    expect(assetCount(loaded)).toBe(0)
  })

  it('keeps successful slots when another asset fails', async () => {
    let calls = 0
    const loaded = await loadSceneAssets({
      loadAsset: async () => {
        calls += 1
        if (calls === 1) return { width: 1024 }
        throw new Error('missing')
      },
      timeoutMs: 50,
    })

    expect(assetCount(loaded)).toBe(1)
  })
})
