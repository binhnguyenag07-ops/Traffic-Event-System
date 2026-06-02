import { Pause, Play, RotateCcw, StepForward } from 'lucide-react';

export function ControlPanel({ simulation }) {
  return (
    <section className="panel">
      <div className="panel-header">
        <h2>Simulation</h2>
        <span className="tick">Tick {simulation.tick}</span>
      </div>
      <div className="icon-row">
        <button type="button" title="Start" aria-label="Start" onClick={simulation.start}>
          <Play size={18} aria-hidden="true" />
        </button>
        <button type="button" title="Pause" aria-label="Pause" onClick={simulation.pause}>
          <Pause size={18} aria-hidden="true" />
        </button>
        <button type="button" title="Next tick" aria-label="Next tick" onClick={simulation.advanceTick}>
          <StepForward size={18} aria-hidden="true" />
        </button>
        <button type="button" title="Reset" aria-label="Reset" onClick={simulation.reset}>
          <RotateCcw size={18} aria-hidden="true" />
        </button>
      </div>
      <label className="field">
        <span>Speed</span>
        <input
          type="range"
          min="0.25"
          max="4"
          step="0.25"
          defaultValue={simulation.speedMultiplier}
          onChange={(event) => simulation.changeSpeed(Number(event.target.value))}
        />
      </label>
    </section>
  );
}
