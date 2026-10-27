package io.forgeloop.control.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface GithubDeliveryRepository extends JpaRepository<GithubDelivery, String> {
    boolean existsByDeliveryId(String deliveryId);

    /** Releases a delivery claim whose processing failed so that a GitHub redelivery is processed again. */
    @Transactional long deleteByDeliveryId(String deliveryId);
}
