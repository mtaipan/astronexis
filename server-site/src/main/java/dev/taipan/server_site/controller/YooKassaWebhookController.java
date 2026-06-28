package dev.taipan.server_site.controller;

import dev.taipan.server_site.service.FulfillmentService;
import dev.taipan.server_site.service.PaymentConfirmationService;
import dev.taipan.server_site.yookassa.YooKassaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

/**
 * Webhook ЮKassa.
 *
 * БЕЗОПАСНОСТЬ (SEC-1): тело уведомления НЕ доверенное — его можно подделать. Из тела берём
 * только id платежа, а реальный статус/сумму запрашиваем у API ЮKassa нашими credentials.
 * Статический query-token — лишь дешёвый фильтр от спама, не граница доверия.
 *
 * Контроллер не лезет в БД напрямую: сетевой вызов идёт вне транзакции, затем
 * {@link PaymentConfirmationService} в короткой транзакции применяет статус, а
 * {@link FulfillmentService} (после коммита) доставляет VIP.
 */
@RestController
@RequestMapping("/yookassa")
public class YooKassaWebhookController {

    private static final Logger log = LoggerFactory.getLogger(YooKassaWebhookController.class);

    private final YooKassaClient yoo;
    private final PaymentConfirmationService confirmation;
    private final FulfillmentService fulfillment;

    @Value("${yookassa.webhook-token:}")
    private String webhookToken;

    public YooKassaWebhookController(YooKassaClient yoo,
                                     PaymentConfirmationService confirmation,
                                     FulfillmentService fulfillment) {
        this.yoo = yoo;
        this.confirmation = confirmation;
        this.fulfillment = fulfillment;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestParam(name = "token", required = false) String token,
            @RequestBody Map<String, Object> body
    ) {
        if (webhookToken != null && !webhookToken.isBlank()) {
            if (token == null || !webhookToken.equals(token)) {
                return ResponseEntity.status(403).body("forbidden");
            }
        }

        // Из тела — только id, всё остальное перепроверяем через API.
        if (!(body.get("object") instanceof Map<?, ?> notified)) {
            return ResponseEntity.ok("ok");
        }
        String providerPaymentId = str(notified.get("id"));
        if (providerPaymentId == null) {
            return ResponseEntity.ok("ok");
        }

        // Источник правды — ответ API (вне транзакции).
        Map<String, Object> real;
        try {
            real = yoo.getPayment(providerPaymentId);
        } catch (Exception e) {
            log.warn("YooKassa getPayment failed for {}: {}", providerPaymentId, e.toString());
            return ResponseEntity.status(502).body("verification failed"); // ЮKassa повторит
        }
        if (real == null) {
            return ResponseEntity.ok("ok");
        }

        String realStatus = str(real.get("status"));
        UUID internalPid = extractInternalPid(real);
        BigDecimal apiAmount = extractAmount(real);

        PaymentConfirmationService.Result result =
                confirmation.confirm(providerPaymentId, internalPid, realStatus, apiAmount);

        // Доставка VIP — после коммита транзакции подтверждения.
        if (result.grantToDeliver() != null) {
            try {
                fulfillment.tryDeliver(result.grantToDeliver());
            } catch (Exception e) {
                // Не страшно: грант остался PENDING, его дотянет планировщик.
                log.warn("Immediate delivery failed grant={}: {}", result.grantToDeliver(), e.toString());
            }
        }

        return ResponseEntity.ok("ok");
    }

    @SuppressWarnings("unchecked")
    private static UUID extractInternalPid(Map<String, Object> paymentObject) {
        if (!(paymentObject.get("metadata") instanceof Map<?, ?> metaRaw)) return null;
        String pid = str(((Map<String, Object>) metaRaw).get("internal_payment_id"));
        if (pid == null) return null;
        try {
            return UUID.fromString(pid);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static BigDecimal extractAmount(Map<String, Object> paymentObject) {
        if (!(paymentObject.get("amount") instanceof Map<?, ?> amountRaw)) return null;
        String value = str(amountRaw.get("value"));
        if (value == null) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String str(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v);
        return s.isBlank() ? null : s;
    }
}
