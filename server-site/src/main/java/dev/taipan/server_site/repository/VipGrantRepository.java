package dev.taipan.server_site.repository;

import dev.taipan.server_site.model.GrantStatus;
import dev.taipan.server_site.model.VipGrant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipGrantRepository extends JpaRepository<VipGrant, UUID> {

    Optional<VipGrant> findByPaymentId(UUID paymentId);

    List<VipGrant> findTop50ByStatusAndAttemptsLessThanOrderByCreatedAtAsc(GrantStatus status, int maxAttempts);
}
