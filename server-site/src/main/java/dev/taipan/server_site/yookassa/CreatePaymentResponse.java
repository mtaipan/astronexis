package dev.taipan.server_site.yookassa;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreatePaymentResponse(
        String id,
        String status,
        Confirmation confirmation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Confirmation(
            String type,
            String confirmation_url
    ) {}
}