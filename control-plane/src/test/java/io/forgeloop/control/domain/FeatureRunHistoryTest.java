package io.forgeloop.control.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/** Exercises real SQL filtering, stable ordering, and page boundaries across tenants. */
@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
class FeatureRunHistoryTest {
    @Autowired EntityManager entities;
    @Autowired FeatureRunRepository runs;

    @Test void filtersDisabledAndForeignConnectionsBeforeApplyingPageLimits() {
        connect("tenant-a", "acme/visible", true);
        connect("tenant-a", "acme/disabled", false);
        connect("tenant-b", "other/repo", true);
        FeatureRun first = run("tenant-a", "acme/visible", "first");
        FeatureRun second = run("tenant-a", "acme/visible", "second");
        run("tenant-a", "acme/disabled", "disabled");
        run("tenant-b", "other/repo", "foreign");
        // A historical run must not become visible if its repository changes organizations.
        run("tenant-b", "acme/visible", "former-owner");
        entities.flush();
        entities.createQuery("update FeatureRun run set run.createdAt = :created")
                .setParameter("created", Instant.parse("2026-01-01T00:00:00Z")).executeUpdate();
        entities.clear();
        List<String> expected = List.of(first.getId(), second.getId()).stream().sorted(java.util.Comparator.reverseOrder()).toList();
        assertEquals(expected, runs.findVisible("tenant-a").stream().map(FeatureRun::getId).toList());
        assertEquals(expected.subList(0, 1), runs.findVisiblePage("tenant-a", false, PageRequest.of(0, 1)).stream().map(FeatureRun::getId).toList());
        assertEquals(expected.subList(1, 2), runs.findVisiblePage("tenant-a", false, PageRequest.of(1, 1)).stream().map(FeatureRun::getId).toList());
        assertEquals(List.of(), runs.findVisiblePage("tenant-a", false, PageRequest.of(2, 1)));
    }

    @Test void archiveFilteringDoesNotConsumeActivePageSlots() {
        connect("tenant-a", "acme/visible", true);
        FeatureRun active = run("tenant-a", "acme/visible", "active");
        FeatureRun archived = run("tenant-a", "acme/visible", "archived");
        archived.cancel();
        archived.setArchived(true);
        entities.flush();
        entities.clear();
        assertEquals(List.of(active.getId()), runs.findVisiblePage("tenant-a", false, PageRequest.of(0, 1)).stream().map(FeatureRun::getId).toList());
        assertEquals(List.of(archived.getId()), runs.findVisiblePage("tenant-a", true, PageRequest.of(0, 1)).stream().map(FeatureRun::getId).toList());
        assertEquals(2, runs.findVisiblePage("tenant-a", null, PageRequest.of(0, 10)).size());
    }

    private void connect(String tenant, String repository, boolean enabled) {
        RepositoryConnection connection = new RepositoryConnection(tenant, repository, 1, "main", "forgeloop", "GENERIC", List.of("unit"), 10);
        entities.persist(connection);
        if (!enabled) {
            entities.flush();
            entities.createQuery("update RepositoryConnection connection set connection.enabled = false where connection.id = :id")
                    .setParameter("id", connection.getId()).executeUpdate();
        }
    }

    private FeatureRun run(String tenant, String repository, String source) {
        FeatureRun run = new FeatureRun(tenant, repository, source, source, "Spec", 10, "GENERIC", 1);
        entities.persist(run);
        return run;
    }
}
