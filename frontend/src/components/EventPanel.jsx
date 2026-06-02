import { CircleAlert, Cone, TrafficCone } from 'lucide-react';

const eventTypes = [
  { name: 'Congestion', icon: TrafficCone },
  { name: 'Accident', icon: CircleAlert },
  { name: 'Closure', icon: Cone },
];

export function EventPanel({ events }) {
  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Events</h2>
        <span className="count">{events.length}</span>
      </div>
      <div className="icon-row">
        {eventTypes.map((type) => {
          const Icon = type.icon;
          return (
            <button key={type.name} type="button" title={type.name} aria-label={type.name}>
              <Icon size={18} aria-hidden="true" />
            </button>
          );
        })}
      </div>
      <ul className="compact-list">
        {events.map((event) => (
          <li key={event.id}>{event.id}</li>
        ))}
      </ul>
    </section>
  );
}
