package dev.taipan.server_site.controller;

import dev.taipan.server_site.model.Payment;
import dev.taipan.server_site.model.Platform;
import dev.taipan.server_site.service.PaymentService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

@Controller
public class DonateController {

    private static final Logger log = LoggerFactory.getLogger(DonateController.class);

    private final PaymentService paymentService;

    public DonateController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/donate")
    public String donate(
            @RequestParam(defaultValue = "JAVA") Platform platform,
            @RequestParam @NotBlank String nick,
            @RequestParam BigDecimal amount,
            @RequestParam(defaultValue = "RUB") String currency,
            Model model
    ) {
        try {
            Payment p = paymentService.createDonation(platform, nick, amount, currency);
            return redirectToConfirmation(p, model);
        } catch (IllegalArgumentException e) {
            // Ожидаемые ошибки валидации (ник, сумма, валюта) — показываем текст пользователю.
            model.addAttribute("error", e.getMessage());
            return "pay_error";
        } catch (Exception e) {
            log.error("createDonation failed: {}", e.toString());
            model.addAttribute("error", "Платёжный сервис временно недоступен. Попробуй позже.");
            return "pay_error";
        }
    }

    @PostMapping("/vip")
    public String vip(
            @RequestParam(defaultValue = "JAVA") Platform platform,
            @RequestParam @NotBlank String nick,
            Model model
    ) {
        try {
            Payment p = paymentService.createVip30(platform, nick);
            return redirectToConfirmation(p, model);
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            return "pay_error";
        } catch (Exception e) {
            log.error("createVip30 failed: {}", e.toString());
            model.addAttribute("error", "Платёжный сервис временно недоступен. Попробуй позже.");
            return "pay_error";
        }
    }

    /**
     * Редирект на страницу оплаты ЮKassa. Если ссылки нет (ЮKassa не вернула confirmation_url) —
     * не делаем "redirect:null", а показываем страницу ошибки.
     */
    private String redirectToConfirmation(Payment p, Model model) {
        String url = (p == null) ? null : p.getConfirmationUrl();
        if (url == null || url.isBlank()) {
            log.error("No confirmation URL for payment {}", p == null ? "null" : p.getId());
            model.addAttribute("error", "Не удалось получить ссылку на оплату. Попробуй позже.");
            return "pay_error";
        }
        return "redirect:" + url;
    }
}
