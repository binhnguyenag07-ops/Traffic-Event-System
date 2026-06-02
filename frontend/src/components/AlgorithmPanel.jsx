import { GitBranch, MapPinned, Timer } from 'lucide-react';

const strategies = [
  { name: 'BFS', icon: GitBranch },
  { name: 'Dijkstra', icon: Timer },
  { name: 'A*', icon: MapPinned },
];

export function AlgorithmPanel() {
  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Routing</h2>
      </div>
      <div className="segmented">
        {strategies.map((strategy) => {
          const Icon = strategy.icon;
          return (
            <button key={strategy.name} type="button" title={strategy.name} aria-label={strategy.name}>
              <Icon size={16} aria-hidden="true" />
              <span>{strategy.name}</span>
            </button>
          );
        })}
      </div>
    </section>
  );
}
