package io.forgeloop.control.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.forgeloop.control.domain.DeliveryTask;
import java.util.LinkedHashMap;

/** Serializes the exact source refs and dependency commits captured when a lease is claimed. */
final class LeaseInputReferences {
    private static final ObjectMapper JSON = new ObjectMapper();

    private LeaseInputReferences() { }

    static String capture(DeliveryTask task) {
        if (task == null) throw new IllegalArgumentException("Task is required to capture lease inputs");
        LinkedHashMap<String, Object> refs = new LinkedHashMap<>();
        refs.put("executionBaseRef", task.getExecutionBaseRef());
        refs.put("verificationBaseRef", task.getVerificationBaseRef());
        refs.put("dependencyChangeShas", task.getDependencyChangeShas());
        try {
            return JSON.writeValueAsString(refs);
        } catch (Exception failure) {
            throw new IllegalStateException("Lease inputs could not be serialized", failure);
        }
    }
}
