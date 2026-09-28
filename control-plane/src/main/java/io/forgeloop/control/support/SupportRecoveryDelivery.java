package io.forgeloop.control.support;

/** Delivers a short-lived email proof without returning recovery capabilities to the requester. */
public interface SupportRecoveryDelivery {
    boolean available();
    void sendRecovery(String recipient, String token);
}
