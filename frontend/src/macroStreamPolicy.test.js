import { describe, expect, it } from 'vitest'
import {
  shouldConnectMacroStream,
  shouldPollMacroFallback,
} from './macroStreamPolicy'

describe('macro stream policy', () => {
  it('suspends both full-frame paths while the physical camera owns live state', () => {
    expect(shouldConnectMacroStream(true)).toBe(false)
    expect(shouldPollMacroFallback(true, false)).toBe(false)
    expect(shouldPollMacroFallback(true, true)).toBe(false)
  })

  it('uses SSE normally and polls only when that stream is disconnected', () => {
    expect(shouldConnectMacroStream(false)).toBe(true)
    expect(shouldPollMacroFallback(false, true)).toBe(false)
    expect(shouldPollMacroFallback(false, false)).toBe(true)
  })
})
