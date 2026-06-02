const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

function notImplemented(functionName) {
  return Promise.reject(new Error(`${functionName} is defined for the backend contract but not implemented yet.`));
}

export function getHealth() {
  return notImplemented('getHealth');
}

export function getRoadNetwork() {
  return notImplemented('getRoadNetwork');
}

export function addIntersection(intersection) {
  return notImplemented('addIntersection');
}

export function removeIntersection(intersectionId) {
  return notImplemented('removeIntersection');
}

export function addRoad(road) {
  return notImplemented('addRoad');
}

export function removeRoad(roadId) {
  return notImplemented('removeRoad');
}

export function updateRoadCondition(roadId, condition) {
  return notImplemented('updateRoadCondition');
}

export function loadMap(sourcePath) {
  return notImplemented('loadMap');
}

export function generateSyntheticMap(intersectionCount, roadCount) {
  return notImplemented('generateSyntheticMap');
}

export function calculateRoute(routeRequest) {
  return notImplemented('calculateRoute');
}

export function recalculateRoute(vehicleId) {
  return notImplemented('recalculateRoute');
}

export function switchRoutingStrategy(strategyName) {
  return notImplemented('switchRoutingStrategy');
}

export function getAvailableStrategies() {
  return notImplemented('getAvailableStrategies');
}

export function startSimulation() {
  return notImplemented('startSimulation');
}

export function pauseSimulation() {
  return notImplemented('pauseSimulation');
}

export function resumeSimulation() {
  return notImplemented('resumeSimulation');
}

export function resetSimulation() {
  return notImplemented('resetSimulation');
}

export function advanceSimulationTick() {
  return notImplemented('advanceSimulationTick');
}

export function setSimulationSpeed(speedMultiplier) {
  return notImplemented('setSimulationSpeed');
}

export function getSimulationSnapshot() {
  return notImplemented('getSimulationSnapshot');
}

export function createVehicle(vehicle) {
  return notImplemented('createVehicle');
}

export function removeVehicle(vehicleId) {
  return notImplemented('removeVehicle');
}

export function scheduleTrafficEvent(event) {
  return notImplemented('scheduleTrafficEvent');
}

export function triggerTrafficEvent(eventId) {
  return notImplemented('triggerTrafficEvent');
}

export function resolveTrafficEvent(eventId) {
  return notImplemented('resolveTrafficEvent');
}

export function getActiveEvents() {
  return notImplemented('getActiveEvents');
}

export function getTrafficStatistics() {
  return notImplemented('getTrafficStatistics');
}

export function getAlgorithmStatistics(strategyName) {
  return notImplemented('getAlgorithmStatistics');
}

export function compareAlgorithms() {
  return notImplemented('compareAlgorithms');
}

export { API_BASE_URL };
