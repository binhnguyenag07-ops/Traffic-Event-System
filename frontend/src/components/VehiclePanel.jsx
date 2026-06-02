import { Bus, Car, Siren } from 'lucide-react';

const vehicleTypes = [
  { name: 'Car', icon: Car },
  { name: 'Bus', icon: Bus },
  { name: 'Emergency', icon: Siren },
];

export function VehiclePanel({ vehicles }) {
  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Vehicles</h2>
        <span className="count">{vehicles.length}</span>
      </div>
      <div className="icon-row">
        {vehicleTypes.map((type) => {
          const Icon = type.icon;
          return (
            <button key={type.name} type="button" title={type.name} aria-label={type.name}>
              <Icon size={18} aria-hidden="true" />
            </button>
          );
        })}
      </div>
      <ul className="compact-list">
        {vehicles.map((vehicle) => (
          <li key={vehicle.id}>{vehicle.id}</li>
        ))}
      </ul>
    </section>
  );
}
