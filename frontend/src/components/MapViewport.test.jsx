import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import MapViewport from './MapViewport'

const INTERSECTIONS = [
  { id: 'I1', name: 'Campus Gate', x: 0, y: 0, locationType: 'NORMAL' },
  { id: 'I2', name: 'School Junction', x: 2, y: 0, locationType: 'SCHOOL' },
  { id: 'I3', name: 'Central Market', x: 4, y: 0, locationType: 'COMMERCIAL' },
]

const ROADS = [
  { id: 'R01', startIntersectionId: 'I1', endIntersectionId: 'I2', status: 'OPEN', congestionLevel: 0.1 },
  { id: 'R02', startIntersectionId: 'I2', endIntersectionId: 'I3', status: 'OPEN', congestionLevel: 0.2 },
]

describe('MapViewport (Week 6 visualization)', () => {
  it('renders the map from API data: every intersection and road appears', () => {
    render(<MapViewport intersections={INTERSECTIONS} roads={ROADS} />)

    expect(screen.getByTestId('map-svg')).toBeInTheDocument()
    INTERSECTIONS.forEach((i) =>
      expect(screen.getByTestId(`map-intersection-${i.id}`)).toBeInTheDocument()
    )
    ROADS.forEach((r) => expect(screen.getByTestId(`map-road-${r.id}`)).toBeInTheDocument())
  })

  it('shows an empty state instead of a map when no network is loaded', () => {
    render(<MapViewport intersections={[]} roads={[]} />)
    expect(screen.queryByTestId('map-svg')).not.toBeInTheDocument()
    expect(screen.getByText(/no network loaded/i)).toBeInTheDocument()
  })

  it('highlights the found route as an overlay and marks its roads', () => {
    const route = {
      steps: [
        { from: 'I1', to: 'I2', roadId: 'R01', cost: 0.2 },
        { from: 'I2', to: 'I3', roadId: 'R02', cost: 0.3 },
      ],
    }
    render(<MapViewport intersections={INTERSECTIONS} roads={ROADS} route={route} />)

    expect(screen.getByTestId('map-route-overlay')).toBeInTheDocument()
    expect(screen.getByTestId('map-road-R01')).toHaveAttribute('data-in-route', 'true')
    expect(screen.getByTestId('map-road-R02')).toHaveAttribute('data-in-route', 'true')
  })

  it('renders no route overlay when there is no route', () => {
    render(<MapViewport intersections={INTERSECTIONS} roads={ROADS} />)
    expect(screen.queryByTestId('map-route-overlay')).not.toBeInTheDocument()
    expect(screen.getByTestId('map-road-R01')).toHaveAttribute('data-in-route', 'false')
  })

  it('moves the vehicle marker when a tick advances the vehicle', () => {
    const vehicleAtI1 = [{ id: 'v1', type: 'CAR', currentIntersectionId: 'I1', status: 'MOVING' }]
    const vehicleAtI2 = [{ id: 'v1', type: 'CAR', currentIntersectionId: 'I2', status: 'MOVING' }]

    const { rerender } = render(
      <MapViewport intersections={INTERSECTIONS} roads={ROADS} vehicles={vehicleAtI1} />
    )
    expect(screen.getByTestId('map-vehicle-v1')).toHaveAttribute('data-at', 'I1')

    rerender(<MapViewport intersections={INTERSECTIONS} roads={ROADS} vehicles={vehicleAtI2} />)
    expect(screen.getByTestId('map-vehicle-v1')).toHaveAttribute('data-at', 'I2')
  })

  it('changes the road color when an event blocks it', () => {
    const openRoads = ROADS
    const blockedRoads = [
      { ...ROADS[0], status: 'BLOCKED', congestionLevel: 1 },
      ROADS[1],
    ]
    const accident = [{ id: 'e1', type: 'ACCIDENT', affectedRoadId: 'R01', status: 'ACTIVE' }]

    const { rerender } = render(
      <MapViewport intersections={INTERSECTIONS} roads={openRoads} events={[]} />
    )
    const before = screen.getByTestId('map-road-R01').getAttribute('stroke')
    expect(screen.getByTestId('map-road-R01')).toHaveAttribute('data-status', 'OPEN')
    expect(screen.queryByTestId('map-event-e1')).not.toBeInTheDocument()

    rerender(<MapViewport intersections={INTERSECTIONS} roads={blockedRoads} events={accident} />)
    const after = screen.getByTestId('map-road-R01').getAttribute('stroke')
    expect(screen.getByTestId('map-road-R01')).toHaveAttribute('data-status', 'BLOCKED')
    expect(after).not.toBe(before)
    // The active event shows a badge on the affected road
    expect(screen.getByTestId('map-event-e1')).toBeInTheDocument()
  })

  it('reports clicks on intersections, roads, and vehicles through onSelect', () => {
    const onSelect = vi.fn()
    const vehicles = [{ id: 'v1', type: 'CAR', currentIntersectionId: 'I1', status: 'WAITING' }]
    render(
      <MapViewport
        intersections={INTERSECTIONS}
        roads={ROADS}
        vehicles={vehicles}
        onSelect={onSelect}
      />
    )

    fireEvent.click(screen.getByTestId('map-intersection-I1'))
    expect(onSelect).toHaveBeenLastCalledWith({ type: 'intersection', id: 'I1' })

    fireEvent.click(screen.getByTestId('map-road-R01'))
    expect(onSelect).toHaveBeenLastCalledWith({ type: 'road', id: 'R01' })

    fireEvent.click(screen.getByTestId('map-vehicle-v1'))
    expect(onSelect).toHaveBeenLastCalledWith({ type: 'vehicle', id: 'v1' })
  })
})
