package dev.taipan.server_site.repository;

import dev.taipan.server_site.model.GrantStatus;
import dev.taipan.server_site.model.VipGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VipGrantRepository extends JpaRepository<VipGrant, UUID> {

    Optional<VipGrant> findByPaymentId(UUID paymentId);

    List<VipGrant> findTop50ByStatusAndAttemptsLessThanOrderByCreatedAtAsc(GrantStatus status, int maxAttempts);

    /**
     * Атомарный захват гранта перед RCON-доставкой (SITE-9): переводит PENDING → DELIVERING
     * и инкрементирует attempts одним UPDATE. Возвращает число затронутых строк —
     * 0 означает, что грант уже захвачен другим доставщиком (webhook vs планировщик).
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update VipGrant g
               set g.status = dev.taipan.server_site.model.GrantStatus.DELIVERING,
                   g.attempts = g.attempts + 1,
                   g.claimedAt = :now
             where g.id = :id
               and g.status = dev.taipan.server_site.model.GrantStatus.PENDING
            """)
    int claimForDelivery(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    /**
     * Возвращает в очередь гранты, зависшие в DELIVERING (процесс упал между захватом
     * и записью результата). Вызывается планировщиком перед прогоном ретраев.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            update VipGrant g
               set g.status = dev.taipan.server_site.model.GrantStatus.PENDING
             where g.status = dev.taipan.server_site.model.GrantStatus.DELIVERING
               and g.claimedAt < :threshold
            """)
    int requeueStaleDelivering(@Param("threshold") OffsetDateTime threshold);
}
