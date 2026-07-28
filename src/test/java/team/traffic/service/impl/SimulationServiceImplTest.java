package team.traffic.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import team.traffic.domain.network.RoadNetwork;
import team.traffic.service.RoutingService;
import team.traffic.simulation.SimulationStatus;
import team.traffic.simulation.TrafficSimulator;

class SimulationServiceImplTest {

    @Test
    void scheduledTickFailurePausesPlaybackAndCancelsFutureTicks() throws InterruptedException {
        FailingTrafficSimulator simulator = new FailingTrafficSimulator();
        AtomicReference<SimulationStatus> publishedStatus = new AtomicReference<>();

        try (SimulationServiceImpl service = new SimulationServiceImpl(simulator)) {
            service.setParameters(0.5, 250);
            service.addSnapshotListener(snapshot -> publishedStatus.set(snapshot.status()));
            service.start();

            assertThat(simulator.awaitAttempt(2, TimeUnit.SECONDS)).isTrue();
            awaitStatus(service, SimulationStatus.PAUSED, 2, TimeUnit.SECONDS);
            awaitStatus(publishedStatus, SimulationStatus.PAUSED, 2, TimeUnit.SECONDS);

            assertThat(service.getStatus()).isEqualTo(SimulationStatus.PAUSED);
            assertThat(publishedStatus).hasValue(SimulationStatus.PAUSED);

            Thread.sleep(600);
            assertThat(simulator.attemptCount()).isEqualTo(1);
        }
    }

    private static void awaitStatus(
            SimulationServiceImpl service,
            SimulationStatus expected,
            long timeout,
            TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (service.getStatus() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static void awaitStatus(
            AtomicReference<SimulationStatus> status,
            SimulationStatus expected,
            long timeout,
            TimeUnit unit) throws InterruptedException {
        long deadline = System.nanoTime() + unit.toNanos(timeout);
        while (status.get() != expected && System.nanoTime() < deadline) {
            Thread.sleep(10);
        }
    }

    private static final class FailingTrafficSimulator extends TrafficSimulator {
        private final CountDownLatch attempted = new CountDownLatch(1);
        private final AtomicInteger attempts = new AtomicInteger();

        private FailingTrafficSimulator() {
            super(new RoadNetwork(), noOpRoutingService());
        }

        @Override
        public synchronized void advanceTick() {
            attempts.incrementAndGet();
            attempted.countDown();
            throw new IllegalStateException("deterministic scheduled-tick failure");
        }

        private boolean awaitAttempt(long timeout, TimeUnit unit) throws InterruptedException {
            return attempted.await(timeout, unit);
        }

        private int attemptCount() {
            return attempts.get();
        }

        private static RoutingService noOpRoutingService() {
            return (network, request) -> null;
        }
    }
}
