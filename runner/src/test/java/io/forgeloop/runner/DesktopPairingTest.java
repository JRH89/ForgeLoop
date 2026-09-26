package io.forgeloop.runner;

import java.time.Duration;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DesktopPairingTest {
    @Test void pendingThenApprovedReturnsIdentity()throws Exception{
        AtomicLong clock=new AtomicLong();AtomicInteger polls=new AtomicInteger();
        var pairing=new DesktopPairing(clock::get,millis->clock.addAndGet(millis*1_000_000));
        var identity=new RunnerIdentity("runner","fake-proof");
        assertSame(identity,pairing.await(()->polls.incrementAndGet()==3?identity:null,()->false,Duration.ofSeconds(10)));
        assertEquals(3,polls.get());
    }
    @Test void expiresAndCanStartFreshWithoutSleepingInTests()throws Exception{
        AtomicLong clock=new AtomicLong();var pairing=new DesktopPairing(clock::get,millis->clock.addAndGet(millis*1_000_000));
        assertNull(pairing.await(()->null,()->false,Duration.ofSeconds(4)));
        assertNotNull(pairing.await(()->new RunnerIdentity("fresh","fake"),()->false,Duration.ofSeconds(4)));
    }
    @Test void cancellationStopsPollingButDoesNotDiscardAnIssuedIdentity()throws Exception{
        AtomicBoolean cancelled=new AtomicBoolean(true);
        var pairing=new DesktopPairing(()->0,millis->fail("No sleep expected"));
        assertNull(pairing.await(()->{fail("Cancelled attempts must not poll");return null;},cancelled::get,Duration.ofMinutes(1)));
        cancelled.set(false);var identity=new RunnerIdentity("issued","fake");
        assertSame(identity,pairing.await(()->{cancelled.set(true);return identity;},cancelled::get,Duration.ofMinutes(1)));
    }
    @Test void connectivityFailuresSurfaceWithoutInfiniteRetries(){
        assertThrows(java.io.IOException.class,()->new DesktopPairing().await(()->{throw new java.io.IOException("offline");},()->false,Duration.ofMinutes(1)));
    }
}
