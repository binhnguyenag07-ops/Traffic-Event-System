/**
 * Physical View owns a lightweight, viewport-bounded state feed. Keeping the
 * district-wide snapshot stream alive beside it would parse and reconcile the
 * full fleet even though the macro UI is not mounted.
 */
export function shouldConnectMacroStream(physicalLiveMode) {
  return !physicalLiveMode
}

export function shouldPollMacroFallback(physicalLiveMode, streamConnected) {
  return !physicalLiveMode && !streamConnected
}
