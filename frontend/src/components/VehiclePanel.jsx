import { VEHICLE_STATUS_COLOR } from '../theme'

/**
 * Vehicle table fed by the simulation snapshot.
 *
 * Props: vehicles (snapshot vehicles with route/progress fields),
 * selected / onSelect shared with the map.
 */
export default function VehiclePanel({ vehicles = [], selected = null, onSelect = () => {} }) {
  return (
    <section className="sim-panel">
      <h2>Vehicles</h2>

      {vehicles.length === 0 ? (
        <p className="sim-hint">No vehicles — build a scenario and initialize the simulation.</p>
      ) : (
        <div className="vehicle-table-wrap">
          <table className="steps-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Type</th>
                <th>Current</th>
                <th>Destination</th>
                <th>Status</th>
                <th>Progress</th>
              </tr>
            </thead>
            <tbody>
              {vehicles.map((v) => {
                const totalSteps = v.routeSteps?.length ?? 0
                const done = Math.min(v.nextRouteStepIndex ?? 0, totalSteps)
                const isSelected = selected?.type === 'vehicle' && selected.id === v.id
                return (
                  <tr
                    key={v.id}
                    className={isSelected ? 'row-selected' : ''}
                    style={{ cursor: 'pointer' }}
                    onClick={() => onSelect({ type: 'vehicle', id: v.id })}
                  >
                    <td><code>{v.id}</code></td>
                    <td>{v.type}</td>
                    <td><code>{v.currentIntersectionId}</code></td>
                    <td><code>{v.destinationIntersectionId}</code></td>
                    <td>
                      <span
                        className="v-status"
                        style={{ color: VEHICLE_STATUS_COLOR[v.status] ?? '#64748b' }}
                      >
                        {v.status}
                      </span>
                    </td>
                    <td>
                      {totalSteps > 0 ? `${done} / ${totalSteps} steps` : '—'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
