// @vitest-environment jsdom
import { act, fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import TimelineScrubber from './TimelineScrubber'

const timeline = {
  recordedSeconds: [0, 1, 2],
}

describe('TimelineScrubber', () => {
  it('debounces and seeks the exact changed frame index', async () => {
    vi.useFakeTimers()
    const onSeek = vi.fn()
    render(<TimelineScrubber timeline={timeline}
      frame={{ elapsedSeconds: 2, frameId: 8 }} viewingHistory={false} loading={false}
      onSeek={onSeek} onLive={() => {}} roadCount={3} intersectionCount={2} incidentCount={0} />)
    const slider = screen.getByRole('slider', { name: 'Recorded simulation frame' })

    fireEvent.change(slider, { target: { value: '0' } })

    expect(onSeek).not.toHaveBeenCalled()
    await act(async () => { vi.advanceTimersByTime(120) })

    expect(onSeek).toHaveBeenCalledWith(0)
    vi.useRealTimers()
  })
})
