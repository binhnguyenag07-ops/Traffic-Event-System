package team.traffic.simulation;

public class SimulationClock {
    private long currentTick;
    private long tickMillis;

    public SimulationClock(long tickMillis) {
        this.tickMillis = tickMillis;
    }

    public void advance() {
        currentTick++;
    }

    public void reset() {
        currentTick = 0L;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public long getTickMillis() {
        return tickMillis;
    }

    public void setTickMillis(long tickMillis) {
        this.tickMillis = tickMillis;
    }
}
