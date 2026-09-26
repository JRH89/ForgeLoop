package io.forgeloop.runner;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

/** Bounded polling, independently testable without a browser, network, or wall-clock waits. */
final class DesktopPairing {
    @FunctionalInterface interface Exchange { RunnerIdentity poll() throws Exception; }
    @FunctionalInterface interface Sleeper { void sleep(long millis) throws InterruptedException; }
    private final LongSupplier clock;
    private final Sleeper sleeper;
    DesktopPairing() { this(System::nanoTime, Thread::sleep); }
    DesktopPairing(LongSupplier clock, Sleeper sleeper) { this.clock=clock; this.sleeper=sleeper; }
    RunnerIdentity await(Exchange exchange, BooleanSupplier cancelled, Duration timeout) throws Exception {
        long started=clock.getAsLong();
        while(!cancelled.getAsBoolean() && clock.getAsLong()-started<timeout.toNanos()) {
            RunnerIdentity identity=exchange.poll();
            // Persist an issued identity even if cancellation raced the successful exchange.
            if(identity!=null)return identity;
            if(!cancelled.getAsBoolean())sleeper.sleep(2000);
        }
        return null;
    }
}
