/**
 * Vehicle Panel Component
 * Displays simulation vehicles from the OOP Vehicle model.
 */
export default function VehiclePanel({ simulationHook }) {
  const { simulationState, getMovingVehicles, getVehicleStats } = simulationHook

  if (!simulationState) {
    return (
      <div className="vehicle-panel">
        <h2>Vehicles</h2>
        <p className="no-data">No simulation loaded</p>
      </div>
    )
  }

  const allVehicles = simulationState.vehicles
  const movingVehicles = getMovingVehicles()
  const vehicleStats = getVehicleStats()

  return (
    <div className="vehicle-panel">
      <h2>Vehicles</h2>

      <div className="vehicle-section">
        <h3>Fleet Statistics</h3>
        <div className="stats-grid">
          <div className="stat-card">
            <span className="stat-label">Total Vehicles:</span>
            <span className="stat-value">{allVehicles.length}</span>
          </div>
          <div className="stat-card">
            <span className="stat-label">Moving:</span>
            <span className="stat-value">{movingVehicles.length}</span>
          </div>
          {Object.entries(vehicleStats).map(([type, count]) => (
            <div key={type} className="stat-card">
              <span className="stat-label">{type}:</span>
              <span className="stat-value">{count}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="vehicle-section">
        <h3>Moving Vehicles ({movingVehicles.length})</h3>
        {movingVehicles.length === 0 ? (
          <p className="no-data">No vehicles moving</p>
        ) : (
          <div className="vehicles-list">
            {movingVehicles.map((vehicle) => (
              <div key={vehicle.id} className="vehicle-item">
                <div className="vehicle-header">
                  <span className="vehicle-id">{vehicle.id}</span>
                  <span className="vehicle-type">{vehicle.getTypeName()}</span>
                </div>
                <div className="vehicle-details">
                  <p>
                    <strong>Location:</strong> {vehicle.currentIntersectionId}
                  </p>
                  <p>
                    <strong>Destination:</strong> {vehicle.destinationIntersectionId}
                  </p>
                  <p>
                    <strong>Status:</strong>{' '}
                    <span
                      className="status-badge"
                      style={{ backgroundColor: vehicle.getStatusColor() }}
                    >
                      {vehicle.getStatusName()}
                    </span>
                  </p>
                  <p>
                    <strong>Distance Traveled:</strong> {vehicle.getTravelDistance()} intersections
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="vehicle-section">
        <h3>All Vehicles ({allVehicles.length})</h3>
        <table className="vehicles-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Type</th>
              <th>Location</th>
              <th>Destination</th>
              <th>Status</th>
              <th>Progress</th>
            </tr>
          </thead>
          <tbody>
            {allVehicles.map((vehicle) => (
              <tr key={vehicle.id}>
                <td>{vehicle.id}</td>
                <td>{vehicle.getTypeName()}</td>
                <td>{vehicle.currentIntersectionId}</td>
                <td>{vehicle.destinationIntersectionId}</td>
                <td>
                  <span
                    className="status-badge"
                    style={{ backgroundColor: vehicle.getStatusColor() }}
                  >
                    {vehicle.getStatusName()}
                  </span>
                </td>
                <td>
                  <span className={vehicle.hasReachedDestination() ? 'arrived' : 'traveling'}>
                    {vehicle.getProgressName()}
                  </span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="vehicle-section">
        <h3>Vehicle Type Distribution</h3>
        <div className="distribution-chart">
          {Object.entries(vehicleStats).map(([type, count]) => {
            const percentage = allVehicles.length > 0 ? (count / allVehicles.length) * 100 : 0
            return (
              <div key={type} className="distribution-item">
                <span className="type-label">{type}</span>
                <div className="bar-container">
                  <div className="bar-fill" style={{ width: `${percentage}%` }}>
                    {percentage > 5 && <span className="percentage">{percentage.toFixed(0)}%</span>}
                  </div>
                </div>
                <span className="count">{count}</span>
              </div>
            )
          })}
        </div>
      </div>
    </div>
  )
}
