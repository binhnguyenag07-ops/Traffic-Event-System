package team.traffic.simulation;

public class SimulationClock {
    private long currentTick;

    public SimulationClock() {
        this.currentTick = 0;
    }

    public void tick() {
        currentTick++;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public void reset() {
        currentTick = 0;
    }
}
