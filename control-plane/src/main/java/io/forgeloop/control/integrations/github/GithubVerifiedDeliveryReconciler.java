package io.forgeloop.control.integrations.github;

import io.forgeloop.control.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.LoggerFactory;

/** Publishes verified runs without a human click only when their captured policy permits it. */
@Service
public class GithubVerifiedDeliveryReconciler {
    private final GithubPublicationRepository publications;
    private final FeatureRunRepository runs;
    private final RepositoryConnectionRepository connections;
    private final GithubDeliveryService delivery;
    public GithubVerifiedDeliveryReconciler(GithubPublicationRepository publications, FeatureRunRepository runs,
            RepositoryConnectionRepository connections, GithubDeliveryService delivery) {
        this.publications=publications; this.runs=runs; this.connections=connections; this.delivery=delivery;
    }
    @Scheduled(fixedDelayString="${forgeloop.github.verified-delivery-reconcile-ms:30000}")
    @Transactional
    public void reconcilePending() {
        for (GithubPublication publication : publications.findByPullRequestNumberIsNullAndHeadShaIsNotNull()) {
            FeatureRun run=runs.findById(publication.getFeatureRunId()).orElse(null);
            if(run==null || run.getState()!=RunState.READY_FOR_REVIEW || !run.isDeliveryAuthorized())continue;
            RepositoryConnection connection=connections.findByRepository(run.getRepository()).orElse(null);
            if(connection==null || !connection.isEnabled() || !connection.getOrganizationId().equals(run.getOrganizationId()))continue;
            try {
                delivery.deliverPushed(run,connection.getInstallationId(),"All required ForgeLoop verification gates passed with checksummed evidence.");
            } catch (RuntimeException failure) {
                LoggerFactory.getLogger(getClass()).warn("Verified delivery will retry for run {}: {}",run.getId(),failure.getClass().getSimpleName());
            }
        }
    }
}
