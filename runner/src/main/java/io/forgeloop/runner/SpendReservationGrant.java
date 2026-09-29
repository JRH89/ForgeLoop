package io.forgeloop.runner;

/** Minimal control-plane response needed by the runner before it may send a provider turn. */
public record SpendReservationGrant(boolean granted, long reservedMicros) { }
