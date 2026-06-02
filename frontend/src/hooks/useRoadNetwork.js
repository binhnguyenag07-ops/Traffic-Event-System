const initialRoadNetwork = {
  intersections: [],
  roads: [],
};

export function useRoadNetwork() {
  function refreshNetwork() {
    // TODO: call getRoadNetwork() from trafficApi.
  }

  function loadMapFile(sourcePath) {
    // TODO: call loadMap(sourcePath) from trafficApi.
  }

  function generateMap(intersectionCount, roadCount) {
    // TODO: call generateSyntheticMap(intersectionCount, roadCount) from trafficApi.
  }

  return {
    ...initialRoadNetwork,
    refreshNetwork,
    loadMapFile,
    generateMap,
  };
}
