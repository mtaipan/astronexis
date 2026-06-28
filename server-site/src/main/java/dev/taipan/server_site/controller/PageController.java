package dev.taipan.server_site.controller;

import dev.taipan.server_site.model.Payment;
import dev.taipan.server_site.repository.PaymentRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.OffsetDateTime;
import java.util.UUID;

@Controller
public class PageController {

    private final PaymentRepository payments;

    public PageController(PaymentRepository payments) {
        this.payments = payments;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("topAll", payments.topAllTime().stream().limit(20).toList());
        model.addAttribute("top30", payments.topSince(OffsetDateTime.now().minusDays(30)).stream().limit(20).toList());
        return "index";
    }

    @GetMapping("/offer")
    public String offer(Model model) {
        model.addAttribute("title", "Оферта — Astronexis");
        return "offer";
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("title", "Конфиденциальность — Astronexis");
        return "privacy";
    }

    @GetMapping("/contacts")
    public String contacts() { return "contacts"; }

    @GetMapping("/delivery")
    public String delivery() { return "delivery"; }

    @GetMapping("/pay/return")
    public String payReturn(@RequestParam(name = "pid", required = false) UUID pid, Model model) {
        Payment p = (pid == null) ? null : payments.findById(pid).orElse(null);

        model.addAttribute("pid", pid);
        model.addAttribute("status", p != null ? String.valueOf(p.getStatus()).toLowerCase() : null);
        model.addAttribute("nick", p != null ? p.getNick() : null);
        model.addAttribute("type", p != null ? String.valueOf(p.getType()) : null);
        model.addAttribute("amount", p != null ? p.getAmountOriginal() : null);
        model.addAttribute("currency", p != null ? p.getCurrencyOriginal() : null);

        return "pay_return";
    }
}