package dev.taipan.server_site.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "site")
public class SiteProperties {

    private String baseUrl = "https://astronexis.site";
    private String title = "Astronexis";

    private String supportEmail = "support@astronexis.site";
    private String supportTelegram = "@astronexis_bot";

    // Реквизиты (ПДн) — реальные значения приходят из ENV (application.yml: ${PAYEE_*}).
    private String payeeType = "Самозанятый (НПД)";
    private String payeeName = "";
    private String payeeInn = "";

    // ОГРНИП — только если ИП. Иначе пусто.
    private String payeeOgrnip = "";

    // Для оферты
    private String offerPublishedAt = "";

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSupportEmail() { return supportEmail; }
    public void setSupportEmail(String supportEmail) { this.supportEmail = supportEmail; }

    public String getSupportTelegram() { return supportTelegram; }
    public void setSupportTelegram(String supportTelegram) { this.supportTelegram = supportTelegram; }

    public String getPayeeType() { return payeeType; }
    public void setPayeeType(String payeeType) { this.payeeType = payeeType; }

    public String getPayeeName() { return payeeName; }
    public void setPayeeName(String payeeName) { this.payeeName = payeeName; }

    public String getPayeeInn() { return payeeInn; }
    public void setPayeeInn(String payeeInn) { this.payeeInn = payeeInn; }

    public String getPayeeOgrnip() { return payeeOgrnip; }
    public void setPayeeOgrnip(String payeeOgrnip) { this.payeeOgrnip = payeeOgrnip; }

    public String getOfferPublishedAt() { return offerPublishedAt; }
    public void setOfferPublishedAt(String offerPublishedAt) { this.offerPublishedAt = offerPublishedAt; }
}