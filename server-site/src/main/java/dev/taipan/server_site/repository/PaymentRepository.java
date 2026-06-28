package dev.taipan.server_site.repository;

import dev.taipan.server_site.model.Payment;
import dev.taipan.server_site.model.PaymentStatus;
import dev.taipan.server_site.model.PaymentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    List<Payment> findTop20ByTypeAndStatusOrderBySucceededAtDesc(PaymentType type, PaymentStatus status);

    @Query("""
        select p.nick as nick, sum(p.amountRub) as totalRub
        from Payment p
        where p.type = 'DONATION' and p.status = 'SUCCEEDED'
        group by p.nick
        order by sum(p.amountRub) desc
    """)
    List<TopRow> topAllTime();

    @Query("""
        select p.nick as nick, sum(p.amountRub) as totalRub
        from Payment p
        where p.type = 'DONATION'
          and p.status = 'SUCCEEDED'
          and p.succeededAt >= :from
        group by p.nick
        order by sum(p.amountRub) desc
    """)
    List<TopRow> topSince(@Param("from") OffsetDateTime from);

    interface TopRow {
        String getNick();
        java.math.BigDecimal getTotalRub();
    }
}