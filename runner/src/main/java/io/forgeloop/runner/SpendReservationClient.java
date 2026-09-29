package io.forgeloop.runner;

/** Authenticated control-plane call bound to the current runner and task lease. */
@FunctionalInterface
public interface SpendReservationClient {
    SpendReservationGrant reserve(long micros) throws Exception;
}
