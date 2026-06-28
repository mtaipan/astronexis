package dev.taipan.server_site.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Периодически дотягивает невыданные VIP-гранты (сервер был оффлайн, RCON моргнул и т.п.).
 * Делает доставку надёжной без участия webhook.
 */
@Component
public class VipGrantRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(VipGrantRetryScheduler.class);

    private final FulfillmentService fulfillment;

    public VipGrantRetryScheduler(FulfillmentService fulfillment) {
        this.fulfillment = fulfillment;
    }

    // каждые 2 минуты, первый прогон через 30с после старта
    @Scheduled(initialDelay = 30_000, fixedDelay = 120_000)
    public void retryPending() {
        try {
            int delivered = fulfillment.deliverPending();
            if (delivered > 0) {
                log.info("VipGrantRetryScheduler: доставлено грантов: {}", delivered);
            }
        } catch (Exception e) {
            log.warn("VipGrantRetryScheduler: ошибка прогона: {}", e.toString());
        }
    }
}
