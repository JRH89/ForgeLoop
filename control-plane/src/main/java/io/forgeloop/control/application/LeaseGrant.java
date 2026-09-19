package io.forgeloop.control.application;
import io.forgeloop.control.domain.TaskLease;
/** The nonce is returned once to the runner and stored only as a hash. */
public record LeaseGrant(TaskLease lease,String nonce){}
