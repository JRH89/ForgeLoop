package io.forgeloop.runner;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** A failed renewal stops further tools/provider turns; it never retries work under an uncertain lease. */
final class LeaseKeeper implements AutoCloseable {
    @FunctionalInterface interface Renewal { void renew() throws Exception; }
    private final ScheduledExecutorService executor;
    private final AtomicBoolean lost;
    private final Renewal renewal;

    LeaseKeeper(Renewal renewal, AtomicBoolean lost, Duration interval) {
        if (renewal == null || lost == null || interval == null || interval.toMillis() < 1)
            throw new IllegalArgumentException("Lease keeper configuration is invalid");
        this.renewal = renewal;
        this.lost = lost;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "forgeloop-lease-renewal");
            thread.setDaemon(true);
            return thread;
        });
        renewNow();
        if (!lost.get()) executor.scheduleWithFixedDelay(this::renewNow, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void renewNow() {
        if (lost.get()) return;
        try { renewal.renew(); }
        catch (Exception unavailable) { lost.set(true); }
    }

    @Override public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(35, TimeUnit.SECONDS)) {
                lost.set(true);
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            lost.set(true);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
