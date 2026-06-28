package dev.taipan.server_site.yookassa;

public record CreatePaymentRequest(
        String internalPaymentId,
        String amountValue,  // "199.00"
        String currency,     // "RUB" in MVP
        String description,
        String returnUrl
) {}