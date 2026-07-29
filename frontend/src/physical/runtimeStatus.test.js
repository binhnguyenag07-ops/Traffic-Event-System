import { describe, expect, it } from 'vitest'
import {
  PHYSICAL_STATUS_POLL_INTERVAL_MS,
  physicalCameraPresentation,
} from './runtimeStatus'

describe('physical runtime status presentation', () => {
  it('reports the real running rate from authoritative parameters', () => {
    expect(physicalCameraPresentation('RUNNING', 0.25, 250)).toMatchObject({
      cameraLabel: 'Live camera',
      paceLabel: '1.0× simulation time',
      tone: 'running',
    })
  })

  it('never labels a paused or ready frame as live', () => {
    expect(physicalCameraPresentation('PAUSED', 30, 250)).toMatchObject({
      cameraLabel: 'Paused at current frame',
      paceLabel: 'clock not advancing',
    })
    expect(physicalCameraPresentation('READY', 0.25, 250).cameraLabel).toBe('ready frame')
  })

  it('marks a failed lightweight status feed without inventing simulation state', () => {
    expect(physicalCameraPresentation('RUNNING', 0.25, 250, false)).toMatchObject({
      cameraLabel: 'Simulation status unavailable',
      tone: 'disconnected',
    })
    expect(PHYSICAL_STATUS_POLL_INTERVAL_MS).toBe(1000)
  })
})
