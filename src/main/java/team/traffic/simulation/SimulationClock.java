package team.traffic.simulation;

public class SimulationClock {
    private long currentTick;
    private double elapsedSeconds;

    public SimulationClock() {
        this.currentTick = 0;
        this.elapsedSeconds = 0.0;
    }

    public void tick(double simulationSeconds) {
        if (!Double.isFinite(simulationSeconds) || simulationSeconds <= 0) {
            throw new IllegalArgumentException("Simulation seconds must be positive.");
        }
        currentTick++;
        elapsedSeconds += simulationSeconds;
    }

    /** Compatibility helper for callers that model one second per tick. */
    public void tick() {
        tick(1.0);
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public double getElapsedSeconds() {
        return elapsedSeconds;
    }

    public void reset() {
        currentTick = 0;
        elapsedSeconds = 0.0;
    }
}
