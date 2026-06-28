package dev.taipan.server_site.yookassa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Component
public class YooKassaClient {

    private final RestClient http;
    private final String shopId;
    private final String secretKey;

    public YooKassaClient(
            RestClient.Builder builder,
            @Value("${yookassa.shop-id}") String shopId,
            @Value("${yookassa.secret-key}") String secretKey
    ) {
        this.shopId = shopId;
        this.secretKey = secretKey;

        // Таймауты обязательны: без них зависший платёжный API заблокирует поток запроса.
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(Duration.ofSeconds(5));
        rf.setReadTimeout(Duration.ofSeconds(10));

        this.http = builder
                .baseUrl("https://api.yookassa.ru/v3")
                .requestFactory(rf)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    public CreatePaymentResponse createRedirectPayment(CreatePaymentRequest req) {
        // idempotence key обязателен для безопасных повторов
        String idemKey = UUID.randomUUID().toString();

        Map<String, Object> payload = Map.of(
                "amount", Map.of(
                        "value", req.amountValue(),
                        "currency", req.currency()
                ),
                "capture", true,
                "description", req.description(),
                "confirmation", Map.of(
                        "type", "redirect",
                        "return_url", req.returnUrl()
                ),
                "metadata", Map.of(
                        "internal_payment_id", req.internalPaymentId()
                )
        );

        return http.post()
                .uri("/payments")
                .headers(h -> {
                    h.setBasicAuth(shopId, secretKey);
                    h.add("Idempotence-Key", idemKey);
                })
                .body(payload)
                .retrieve()
                .body(CreatePaymentResponse.class);
    }

    /**
     * Запрашивает реальное состояние платежа у ЮKassa.
     * Используется для верификации webhook: доверять статусу можно ТОЛЬКО ответу API
     * (тело уведомления подделывается, см. SEC-1). Возвращает «сырой» JSON как Map
     * (status, amount, metadata, paid и т.д.) или null при пустом id.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getPayment(String paymentId) {
        if (paymentId == null || paymentId.isBlank()) {
            return null;
        }
        return http.get()
                .uri("/payments/{id}", paymentId)
                .headers(h -> h.setBasicAuth(shopId, secretKey))
                .retrieve()
                .body(Map.class);
    }
}