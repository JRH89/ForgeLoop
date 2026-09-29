package io.forgeloop.control.application;

/** Safe result of the control-plane's atomic task/run spend-budget decision. */
public record SpendReservation(boolean granted, long reservedMicros,
                               Long taskRemainingMicros, long runRemainingMicros) { }
