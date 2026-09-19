package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Customer-controlled execution node; it never uploads raw repository source or provider keys. */
@Entity
@Table(name = "runner")
public class Runner {
  @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
  @Column(nullable = false) private String organizationId;
  @Column(nullable = false) private String name;
  @Column(nullable = false) private String version;
  @Column(nullable = false, length = 2000) private String capabilities;
  @Column(nullable = false) private Instant registeredAt;
  @Column(nullable = false) private Instant lastHeartbeatAt;
  @Column(nullable = false) private boolean enabled;
  protected Runner() { }
  public Runner(String organizationId, String name, String version, List<String> capabilities) { this.organizationId = organizationId; this.name = name; this.version = version; this.capabilities = String.join(",", capabilities); this.registeredAt = Instant.now(); this.lastHeartbeatAt = registeredAt; this.enabled = true; }
  public void heartbeat() { lastHeartbeatAt = Instant.now(); }
  public String getId() { return id; } public String getOrganizationId() { return organizationId; } public String getName() { return name; } public String getVersion() { return version; } public List<String> getCapabilities() { return Arrays.stream(capabilities.split(",")).filter(value -> !value.isBlank()).toList(); } public String getRegisteredAt() { return registeredAt.toString(); } public String getLastHeartbeatAt() { return lastHeartbeatAt.toString(); } public boolean isEnabled() { return enabled; }
}
