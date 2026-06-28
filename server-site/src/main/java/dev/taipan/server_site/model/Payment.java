package dev.taipan.server_site.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@Setter
public class Payment {

    @Id
    @Column(nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Platform platform;

    @Column(nullable = false)
    private String nick;

    @Column(nullable = false)
    private String title;

    @Column(name = "amount_original", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountOriginal;

    @Column(name = "currency_original", nullable = false)
    private String currencyOriginal;

    @Column(name = "amount_rub", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountRub;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(nullable = false)
    private String provider; // "YOOKASSA"

    @Column(name = "provider_payment_id")
    private String providerPaymentId;

    @Column(name = "confirmation_url")
    private String confirmationUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "succeeded_at")
    private OffsetDateTime succeededAt;
}