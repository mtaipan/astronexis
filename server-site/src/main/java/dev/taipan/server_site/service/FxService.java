package dev.taipan.server_site.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class FxService {

    @Value("${fx.usd-to-rub:95.00}")
    private BigDecimal usdToRub;

    @Value("${fx.eur-to-rub:103.00}")
    private BigDecimal eurToRub;

    @Value("${fx.kzt-to-rub:0.20}")
    private BigDecimal kztToRub;

    public BigDecimal toRub(BigDecimal amount, String currency) {
        String c = (currency == null ? "RUB" : currency.trim().toUpperCase());
        if ("RUB".equals(c)) return amount.setScale(2, RoundingMode.HALF_UP);

        BigDecimal rate = switch (c) {
            case "USD" -> usdToRub;
            case "EUR" -> eurToRub;
            case "KZT" -> kztToRub;
            default -> throw new IllegalArgumentException("Unsupported currency for MVP: " + c);
        };

        return amount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }
}