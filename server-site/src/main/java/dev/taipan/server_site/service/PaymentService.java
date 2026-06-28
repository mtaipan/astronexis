package dev.taipan.server_site.service;

import dev.taipan.server_site.model.Payment;
import dev.taipan.server_site.model.PaymentStatus;
import dev.taipan.server_site.model.PaymentType;
import dev.taipan.server_site.model.Platform;
import dev.taipan.server_site.repository.PaymentRepository;
import dev.taipan.server_site.util.NickValidator;
import dev.taipan.server_site.yookassa.CreatePaymentRequest;
import dev.taipan.server_site.yookassa.CreatePaymentResponse;
import dev.taipan.server_site.yookassa.YooKassaClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class PaymentService {

    private static final BigDecimal MIN_RUB = new BigDecimal("1.00");
    private static final BigDecimal MAX_RUB = new BigDecimal("1000000.00");
    private static final BigDecimal VIP_PRICE_RUB = new BigDecimal("199.00");

    private final PaymentRepository payments;
    private final YooKassaClient yoo;
    private final FxService fx;

    @Value("${yookassa.return-url}")
    private String returnUrlBase;

    public PaymentService(PaymentRepository payments, YooKassaClient yoo, FxService fx) {
        this.payments = payments;
        this.yoo = yoo;
        this.fx = fx;
    }

    @Transactional
    public Payment createDonation(Platform platform, String nickRaw, BigDecimal amount, String currency) {
        platform = orDefault(platform);
        String nick = NickValidator.normalizeForPlatform(platform, nickRaw);

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше 0.");
        }
        BigDecimal rub = fx.toRub(amount, currency);
        requireSaneAmount(rub);

        String origCurrency = (currency == null ? "RUB" : currency.trim().toUpperCase());
        return createPayment(
                PaymentType.DONATION, platform, nick,
                "Пожертвование на развитие проекта Astronexis",
                amount, origCurrency, rub,
                "Пожертвование для " + nick
        );
    }

    @Transactional
    public Payment createVip30(Platform platform, String nickRaw) {
        platform = orDefault(platform);
        String nick = NickValidator.normalizeForPlatform(platform, nickRaw);

        return createPayment(
                PaymentType.VIP_30D, platform, nick,
                "VIP на 30 дней",
                VIP_PRICE_RUB, "RUB", VIP_PRICE_RUB,
                "VIP на 30 дней для " + nick
        );
    }

    /** Общий путь создания платежа: запись PENDING → создание в ЮKassa → сохранение ссылки. */
    private Payment createPayment(PaymentType type, Platform platform, String nick, String title,
                                  BigDecimal amountOriginal, String currencyOriginal, BigDecimal rub,
                                  String providerDescription) {
        Payment p = new Payment();
        p.setId(UUID.randomUUID());
        p.setType(type);
        p.setPlatform(platform);
        p.setNick(nick);
        p.setTitle(title);
        p.setAmountOriginal(amountOriginal);
        p.setCurrencyOriginal(currencyOriginal);
        p.setAmountRub(rub);
        p.setStatus(PaymentStatus.PENDING);
        p.setProvider("YOOKASSA");
        p.setCreatedAt(OffsetDateTime.now());
        payments.save(p);

        CreatePaymentResponse resp = yoo.createRedirectPayment(new CreatePaymentRequest(
                p.getId().toString(),
                rub.toPlainString(),
                "RUB",
                providerDescription,
                appendPid(returnUrlBase, p.getId())
        ));

        p.setProviderPaymentId(resp.id());
        p.setConfirmationUrl(resp.confirmation() != null ? resp.confirmation().confirmation_url() : null);
        payments.save(p);

        return p;
    }

    private static Platform orDefault(Platform platform) {
        return platform == null ? Platform.JAVA : platform;
    }

    private static void requireSaneAmount(BigDecimal rub) {
        if (rub.compareTo(MIN_RUB) < 0 || rub.compareTo(MAX_RUB) > 0) {
            throw new IllegalArgumentException("Сумма должна быть от 1 до 1 000 000 ₽.");
        }
    }

    private static String appendPid(String base, UUID pid) {
        if (base == null || base.isBlank()) return null;
        String sep = base.contains("?") ? "&" : "?";
        return base + sep + "pid=" + pid;
    }
}
