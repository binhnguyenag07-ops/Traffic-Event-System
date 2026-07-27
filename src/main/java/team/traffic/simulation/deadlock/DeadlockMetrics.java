package team.traffic.simulation.deadlock;

/** Mutable counters owned by the detector/recovery coordinator. */
public final class DeadlockMetrics {
    private long scans;
    private long suspected;
    private long confirmed;
    private long recovered;
    private long unresolvable;
    private long reservationIssued;
    private long reservationSucceeded;
    private long reservationExpired;
    private long assistedRecoveries;

    void scan() { scans++; }
    void suspect() { suspected++; }
    void confirm() { confirmed++; }
    void recover() { recovered++; }
    void recordUnresolvable() { unresolvable++; }
    void recordReservationIssued() { reservationIssued++; }
    void recordReservationSucceeded() { reservationSucceeded++; }
    void recordReservationExpired() { reservationExpired++; }

    public long scans() { return scans; }
    public long suspected() { return suspected; }
    public long confirmed() { return confirmed; }
    public long recovered() { return recovered; }
    public long unresolvable() { return unresolvable; }
    public long reservationIssued() { return reservationIssued; }
    public long reservationSucceeded() { return reservationSucceeded; }
    public long reservationExpired() { return reservationExpired; }
    public long assistedRecoveries() { return assistedRecoveries; }
}
