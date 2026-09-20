package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;

/** Tenant root; its identifier is the OIDC org_id claim used by all scoped resources. */
@Entity
public class Organization {
    @Id private String id;
    @Column(nullable = false, unique = true) private String name;
    protected Organization() { }
    public Organization(String id, String name) { this.id = required(id, "Organization id"); this.name = required(name, "Organization name"); }
    private static String required(String value, String field) { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value; }
    public String getId() { return id; } public String getName() { return name; }
}
