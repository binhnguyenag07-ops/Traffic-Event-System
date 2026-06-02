export function StatisticsPanel({ statistics }) {
  const metrics = [
    ['Vehicles', statistics.activeVehicleCount],
    ['Avg time', statistics.averageTravelTime],
    ['Congestion', statistics.averageCongestionLevel],
    ['Blocked', statistics.blockedRoadCount],
  ];

  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Statistics</h2>
      </div>
      <div className="metric-grid">
        {metrics.map(([label, value]) => (
          <div className="metric" key={label}>
            <span>{label}</span>
            <strong>{value}</strong>
          </div>
        ))}
      </div>
    </section>
  );
}
