package dev.taipan.auth_tg.gate;

import dev.taipan.auth_tg.config.PluginConfig;
import dev.taipan.auth_tg.store.BindingsStore;
import dev.taipan.auth_tg.store.TrustSession;
import dev.taipan.auth_tg.store.TrustStore;
import dev.taipan.auth_tg.tg.TelegramApi;
import dev.taipan.auth_tg.util.Codes;
import dev.taipan.auth_tg.util.Fingerprints;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class GateListener implements Listener {

    /** Сколько неверных вводов /tgcode допускается до блокировки текущего кода (AUTH-2). */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final JavaPlugin plugin;
    private final PluginConfig cfg;
    private final BindingsStore store;
    private final TrustStore trust;
    private final TelegramApi tg;

    private final Map<UUID, PendingCode> pending = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> authMeLoggedIn = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> codeAttempts = new ConcurrentHashMap<>();

    public GateListener(JavaPlugin plugin, PluginConfig cfg, BindingsStore store, TrustStore trust, TelegramApi tg) {
        this.plugin = plugin;
        this.cfg = cfg;
        this.store = store;
        this.trust = trust;
        this.tg = tg;
    }

    // вызывается из AuthMePoller при смене состояния входа в AuthMe
    public void onAuthMeLogin(Player p, boolean loggedIn) {
        authMeLoggedIn.put(p.getUniqueId(), loggedIn);
        codeAttempts.remove(p.getUniqueId());

        if (!loggedIn) {
            pending.remove(p.getUniqueId());
            applyFreezeState(p);
            return;
        }

        if (hasBypass(p)) {
            applyFreezeState(p);
            return;
        }

        if (isTrustedNow(p)) {
            pending.remove(p.getUniqueId());
            applyFreezeState(p);
            return;
        }

        Long chatId = store.getChatIdByNick(p.getName());
        if (chatId == null) {
            p.sendMessage(ChatColor.RED + "Telegram-bot: @astronexis_bot.");
            p.sendMessage(ChatColor.RED + "Привяжи Telegram: напиши боту /bind <ник>.");
            p.sendMessage(ChatColor.RED + "Потом снова /login.");
            pending.put(p.getUniqueId(), PendingCode.blockedNoTg());
            applyFreezeState(p);
            return;
        }

        String code = Codes.code6();
        Instant exp = Instant.now().plusSeconds(cfg.security().codeTtlSeconds());
        pending.put(p.getUniqueId(), PendingCode.normal(code, exp));

        tg.sendMessage(chatId, "Код входа: " + code + " (TTL " + cfg.security().codeTtlSeconds() + "s)");
        p.sendMessage(ChatColor.YELLOW + "Проверь Telegram @astronexis_bot. Введи: /tgcode <код> чтобы продолжить.");

        applyFreezeState(p);
    }

    private boolean hasBypass(Player p) {
        return cfg.security().allowBypassPermission()
                && p.hasPermission(cfg.security().bypassPermissionNode());
    }

    private boolean isTrustedNow(Player p) {
        TrustSession ts = trust.getValid(p.getUniqueId());
        if (ts == null) return false;

        String fpNow = Fingerprints.fingerprint(p, cfg.security().trustBind(), cfg.security().trustSubnetV4());
        return ts.fingerprint().equals(fpNow);
    }

    public boolean isAuthMeLoggedIn(Player p) {
        return authMeLoggedIn.getOrDefault(p.getUniqueId(), false);
    }

    public boolean isFullyAuthorized(Player p) {
        return isAuthMeLoggedIn(p) && !isBlocked(p);
    }

    public boolean isBlockedNoTg(Player p) {
        PendingCode pc = pending.get(p.getUniqueId());
        return pc != null && pc.noTgBinding;
    }

    public boolean isBlocked(Player p) {
        if (hasBypass(p)) return false;

        PendingCode pc = pending.get(p.getUniqueId());
        if (pc == null) return false;

        if (pc.noTgBinding || pc.locked) return true;

        if (Instant.now().isAfter(pc.expiresAt)) {
            pending.remove(p.getUniqueId());
            p.sendMessage(ChatColor.RED + "Код истёк. Перезайди или повтори /login.");
            return false;
        }
        return true;
    }

    public boolean mustBeFrozen(Player p) {
        if (hasBypass(p)) return false;
        return !isFullyAuthorized(p);
    }

    public boolean tryAcceptCode(Player p, String codeInput) {
        PendingCode pc = pending.get(p.getUniqueId());
        if (pc == null) return true;
        if (pc.noTgBinding || pc.locked) return false;

        if (Instant.now().isAfter(pc.expiresAt)) {
            pending.remove(p.getUniqueId());
            applyFreezeState(p);
            return false;
        }

        String code = normalizeCode(codeInput);
        if (!pc.code.equals(code)) {
            int attempts = codeAttempts.merge(p.getUniqueId(), 1, Integer::sum);
            if (attempts >= MAX_CODE_ATTEMPTS) {
                // Блокируем текущий код от перебора: инвалидируем его и держим заморозку.
                pending.put(p.getUniqueId(), PendingCode.locked());
                codeAttempts.remove(p.getUniqueId());
                applyFreezeState(p);
                p.sendMessage(ChatColor.RED + "Слишком много попыток. Перезайди и повтори /login для нового кода.");
            }
            return false;
        }

        String fp = Fingerprints.fingerprint(p, cfg.security().trustBind(), cfg.security().trustSubnetV4());
        Instant exp = Instant.now().plusSeconds(cfg.security().trustTtlSeconds());
        trust.upsert(p.getUniqueId(), fp, exp);

        pending.remove(p.getUniqueId());
        codeAttempts.remove(p.getUniqueId());
        applyFreezeState(p);
        return true;
    }

    private void applyFreezeState(Player p) {
        if (p == null || !p.isOnline()) return;

        if (mustBeFrozen(p)) {
            hardFreezeNow(p);
        } else {
            hardUnfreezeNow(p);
        }
    }

    private void hardFreezeNow(Player p) {
        p.setVelocity(new Vector(0, 0, 0));
        p.setFallDistance(0f);
        p.setFireTicks(0);

        try {
            p.setFreezeTicks(0);
        } catch (Throwable ignored) {}

        try {
            p.setRemainingAir(p.getMaximumAir());
        } catch (Throwable ignored) {}
    }

    private void hardUnfreezeNow(Player p) {
        p.setFallDistance(0f);
        p.setFireTicks(0);

        try {
            p.setFreezeTicks(0);
        } catch (Throwable ignored) {}

        try {
            p.setRemainingAir(p.getMaximumAir());
        } catch (Throwable ignored) {}
    }

    private static String normalizeCode(String input) {
        if (input == null) return "";
        String code = input.trim();
        if (code.length() >= 2 && code.startsWith("<") && code.endsWith(">")) {
            code = code.substring(1, code.length() - 1).trim();
        }
        return code;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        pending.remove(id);
        authMeLoggedIn.remove(id);
        codeAttempts.remove(id);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (!mustBeFrozen(p)) return;
        if (e.getTo() == null) return;

        boolean changed =
                e.getFrom().getX() != e.getTo().getX() ||
                        e.getFrom().getY() != e.getTo().getY() ||
                        e.getFrom().getZ() != e.getTo().getZ();

        if (changed) {
            e.setTo(e.getFrom());
        }

        hardFreezeNow(p);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && mustBeFrozen(p)) {
            e.setCancelled(true);
            hardFreezeNow(p);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCombust(EntityCombustEvent e) {
        if (e.getEntity() instanceof Player p && mustBeFrozen(p)) {
            e.setCancelled(true);
            p.setFireTicks(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player p) || !mustBeFrozen(p)) return;

        e.setCancelled(true);
        try {
            if (p.getFoodLevel() < 20) {
                p.setFoodLevel(20);
            }
            p.setSaturation(20f);
        } catch (Throwable ignored) {}
    }

    // Пока игрок не прошёл 2FA — запрещаем взаимодействие с миром, инвентарём и чатом.

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        if (mustBeFrozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        if (mustBeFrozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        if (mustBeFrozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        if (mustBeFrozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent e) {
        if (mustBeFrozen(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && mustBeFrozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p && mustBeFrozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && mustBeFrozen(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCmd(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!mustBeFrozen(p)) return;

        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/tgcode")) return;
        if (msg.startsWith("/login")) return;
        if (msg.startsWith("/register")) return;
        if (msg.startsWith("/auth")) return;

        e.setCancelled(true);

        if (!isAuthMeLoggedIn(p)) {
            p.sendMessage(ChatColor.RED + "Сначала войди: /login <пароль> или /register <пароль> <пароль>.");
            p.sendMessage(ChatColor.GRAY + "Bedrock: можно открыть форму через /auth.");
            return;
        }

        if (isBlockedNoTg(p)) {
            p.sendMessage(ChatColor.RED + "Сначала привяжи Telegram: /bind <ник> в боте, затем снова /login.");
            return;
        }

        p.sendMessage(ChatColor.RED + "Сначала введи /tgcode <код> (или /auth для формы на Bedrock).");
    }

    private static final class PendingCode {
        final String code;
        final Instant expiresAt;
        final boolean noTgBinding;
        final boolean locked;

        private PendingCode(String code, Instant expiresAt, boolean noTgBinding, boolean locked) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.noTgBinding = noTgBinding;
            this.locked = locked;
        }

        static PendingCode normal(String code, Instant exp) {
            return new PendingCode(code, exp, false, false);
        }

        static PendingCode blockedNoTg() {
            return new PendingCode("NO_TG", Instant.now().plusSeconds(3600), true, false);
        }

        static PendingCode locked() {
            return new PendingCode("LOCKED", Instant.now().plusSeconds(3600), false, true);
        }
    }
}
