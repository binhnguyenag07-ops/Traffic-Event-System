const initialSimulation = {
  status: 'CREATED',
  tick: 0,
  speedMultiplier: 1,
  vehicles: [],
  events: [],
  selectedRoute: null,
  statistics: {
    activeVehicleCount: 0,
    averageTravelTime: 0,
    averageCongestionLevel: 0,
    blockedRoadCount: 0,
  },
};

export function useSimulation() {
  function start() {
    // TODO: call startSimulation() from trafficApi.
  }

  function pause() {
    // TODO: call pauseSimulation() from trafficApi.
  }

  function resume() {
    // TODO: call resumeSimulation() from trafficApi.
  }

  function reset() {
    // TODO: call resetSimulation() from trafficApi.
  }

  function advanceTick() {
    // TODO: call advanceSimulationTick() from trafficApi.
  }

  function changeSpeed(speedMultiplier) {
    // TODO: call setSimulationSpeed(speedMultiplier) from trafficApi.
  }

  function refreshSnapshot() {
    // TODO: call getSimulationSnapshot() from trafficApi.
  }

  return {
    ...initialSimulation,
    start,
    pause,
    resume,
    reset,
    advanceTick,
    changeSpeed,
    refreshSnapshot,
  };
}
