package dev.taipan.server_site.service;

import dev.taipan.server_site.config.RconProperties;
import dev.taipan.server_site.model.GrantStatus;
import dev.taipan.server_site.model.VipGrant;
import dev.taipan.server_site.rcon.RconClient;
import dev.taipan.server_site.repository.VipGrantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Доставка VIP на игровой сервер (SEC-3 / SITE-3).
 *
 * Два пути выдачи, дополняющие друг друга:
 *  1) «командой» — RCON-команда на сервер (мгновенно, если сервер онлайн);
 *  2) «другим способом» — durable-очередь {@code vip_grants}: грант остаётся PENDING при
 *     любом сбое и переотправляется планировщиком; ту же таблицу может читать игровой плагин
 *     и выдавать VIP при следующем входе игрока (контракт: nick, platform, duration_days).
 *
 * Сетевой вызов RCON вынесен за пределы транзакции подтверждения платежа.
 */
@Service
public class FulfillmentService {

    private static final Logger log = LoggerFactory.getLogger(FulfillmentService.class);

    private final VipGrantRepository grants;
    private final RconClient rcon;
    private final RconProperties rconProps;

    @Value("${vip.group-name:VIP}")
    private String vipGroup;

    public FulfillmentService(VipGrantRepository grants, RconClient rcon, RconProperties rconProps) {
        this.grants = grants;
        this.rcon = rcon;
        this.rconProps = rconProps;
    }

    /** Пытается доставить один грант. Безопасно вызывать повторно (идемпотентно по статусу). */
    public void tryDeliver(UUID grantId) {
        if (grantId == null) {
            return;
        }
        grants.findById(grantId).ifPresent(this::deliver);
    }

    /** Переотправка всех ожидающих грантов (вызывается планировщиком). @return сколько доставлено. */
    public int deliverPending() {
        if (!rconProps.isEnabled()) {
            return 0; // выдача идёт «другим способом» (плагин/вручную) — очередь не трогаем
        }
        List<VipGrant> pending = grants.findTop50ByStatusAndAttemptsLessThanOrderByCreatedAtAsc(
                GrantStatus.PENDING, rconProps.getMaxAttempts());

        int delivered = 0;
        for (VipGrant g : pending) {
            if (deliver(g)) {
                delivered++;
            }
        }
        return delivered;
    }

    private boolean deliver(VipGrant g) {
        if (g.getStatus() != GrantStatus.PENDING) {
            return false;
        }
        if (!rconProps.isEnabled()) {
            // RCON выключен: оставляем в очереди для плагина/ручной выдачи.
            log.info("RCON disabled; VIP grant {} остаётся в очереди (nick={})", g.getId(), g.getNick());
            return false;
        }

        String command = rconProps.getVipCommand()
                .replace("%nick%", g.getNick())
                .replace("%days%", String.valueOf(g.getDurationDays()))
                .replace("%group%", vipGroup);

        g.setAttempts(g.getAttempts() + 1);
        try {
            String resp = rcon.execute(command);
            g.setStatus(GrantStatus.DELIVERED);
            g.setDeliveredAt(OffsetDateTime.now());
            g.setLastError(null);
            grants.save(g);
            log.info("VIP delivered: nick={} grant={} rconResp='{}'", g.getNick(), g.getId(), resp);
            return true;
        } catch (Exception e) {
            g.setLastError(e.getMessage());
            if (g.getAttempts() >= rconProps.getMaxAttempts()) {
                g.setStatus(GrantStatus.FAILED);
                log.error("VIP delivery FAILED (исчерпаны попытки) grant={} nick={}: {}",
                        g.getId(), g.getNick(), e.getMessage());
            } else {
                log.warn("VIP delivery retry {} grant={} nick={}: {}",
                        g.getAttempts(), g.getId(), g.getNick(), e.getMessage());
            }
            grants.save(g);
            return false;
        }
    }
}
