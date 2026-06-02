import { Activity, Network } from 'lucide-react';
import { AlgorithmPanel } from './components/AlgorithmPanel.jsx';
import { ControlPanel } from './components/ControlPanel.jsx';
import { EventPanel } from './components/EventPanel.jsx';
import { MapViewport } from './components/MapViewport.jsx';
import { StatisticsPanel } from './components/StatisticsPanel.jsx';
import { VehiclePanel } from './components/VehiclePanel.jsx';
import { useRoadNetwork } from './hooks/useRoadNetwork.js';
import { useSimulation } from './hooks/useSimulation.js';

export default function App() {
  const roadNetwork = useRoadNetwork();
  const simulation = useSimulation();

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand-mark">
          <Network size={20} aria-hidden="true" />
        </div>
        <div>
          <h1>Urban Traffic Simulator</h1>
          <p>Java backend · React frontend</p>
        </div>
        <div className="status-pill">
          <Activity size={16} aria-hidden="true" />
          <span>{simulation.status}</span>
        </div>
      </header>

      <main className="workspace">
        <aside className="sidebar">
          <ControlPanel simulation={simulation} />
          <AlgorithmPanel />
          <VehiclePanel vehicles={simulation.vehicles} />
        </aside>

        <MapViewport
          intersections={roadNetwork.intersections}
          roads={roadNetwork.roads}
          vehicles={simulation.vehicles}
          selectedRoute={simulation.selectedRoute}
        />

        <aside className="sidebar">
          <EventPanel events={simulation.events} />
          <StatisticsPanel statistics={simulation.statistics} />
        </aside>
      </main>
    </div>
  );
}
