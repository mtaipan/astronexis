package dev.taipan.server_site.service;

import dev.taipan.server_site.model.*;
import dev.taipan.server_site.repository.PaymentRepository;
import dev.taipan.server_site.repository.VipGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Применяет к платежу статус, уже проверенный через API ЮKassa (см. SEC-1).
 * Транзакция короткая и без сетевых вызовов: только чтение/запись БД.
 * При успешной оплате VIP кладёт грант в очередь выдачи (transactional outbox) —
 * сама доставка идёт отдельно ({@link FulfillmentService}).
 */
@Service
public class PaymentConfirmationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentConfirmationService.class);

    private final PaymentRepository payments;
    private final VipGrantRepository grants;

    @Value("${vip.duration-days:30}")
    private int vipDurationDays;

    public PaymentConfirmationService(PaymentRepository payments, VipGrantRepository grants) {
        this.payments = payments;
        this.grants = grants;
    }

    /** Результат подтверждения. grantToDeliver != null — есть свежий VIP-грант для доставки. */
    public record Result(boolean changed, UUID grantToDeliver) {
        static Result noop() { return new Result(false, null); }
    }

    @Transactional
    public Result confirm(String providerPaymentId, UUID internalPid, String realStatus, BigDecimal apiAmount) {
        Optional<Payment> opt = Optional.empty();
        if (internalPid != null) {
            opt = payments.findById(internalPid);
        }
        if (opt.isEmpty() && providerPaymentId != null) {
            opt = payments.findByProviderAndProviderPaymentId("YOOKASSA", providerPaymentId);
        }
        if (opt.isEmpty()) {
            log.info("Confirm: платёж не найден providerId={} internalPid={}", providerPaymentId, internalPid);
            return Result.noop();
        }

        Payment p = opt.get();

        // Идемпотентность.
        if (p.getStatus() == PaymentStatus.SUCCEEDED) {
            return Result.noop();
        }

        // Защитная сверка суммы (у нас всегда RUB).
        if (apiAmount != null && p.getAmountRub() != null && apiAmount.compareTo(p.getAmountRub()) != 0) {
            log.warn("Confirm: расхождение суммы payment={} api={} local={}", p.getId(), apiAmount, p.getAmountRub());
            return Result.noop();
        }

        if ("succeeded".equalsIgnoreCase(realStatus)) {
            p.setStatus(PaymentStatus.SUCCEEDED);
            p.setSucceededAt(OffsetDateTime.now());
            payments.save(p);

            UUID grantId = (p.getType() == PaymentType.VIP_30D) ? enqueueVipGrant(p) : null;
            return new Result(true, grantId);
        }

        if ("canceled".equalsIgnoreCase(realStatus)) {
            p.setStatus(PaymentStatus.CANCELED);
            payments.save(p);
            return new Result(true, null);
        }

        // pending / waiting_for_capture — статус не меняем.
        return Result.noop();
    }

    private UUID enqueueVipGrant(Payment p) {
        // Один грант на платёж (uq_vip_grants_payment) — повторно не создаём.
        Optional<VipGrant> existing = grants.findByPaymentId(p.getId());
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        VipGrant g = new VipGrant();
        g.setId(UUID.randomUUID());
        g.setPaymentId(p.getId());
        g.setNick(p.getNick());
        g.setPlatform(p.getPlatform());
        g.setDurationDays(vipDurationDays);
        g.setStatus(GrantStatus.PENDING);
        g.setAttempts(0);
        g.setCreatedAt(OffsetDateTime.now());
        grants.save(g);

        log.info("VIP grant enqueued: nick={} days={} payment={}", p.getNick(), vipDurationDays, p.getId());
        return g.getId();
    }
}
