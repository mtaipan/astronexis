package dev.taipan.auth_tg.gate;

import dev.taipan.auth_tg.config.PluginConfig;
import dev.taipan.auth_tg.config.SecurityConfig;
import dev.taipan.auth_tg.store.BindingsStore;
import dev.taipan.auth_tg.store.TrustSession;
import dev.taipan.auth_tg.store.TrustStore;
import dev.taipan.auth_tg.tg.TelegramApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Тесты 2FA-гейта. Ключевой — регрессия на SEC-8/AUTH-7: истечение TTL кода
 * раньше «разблокировало» игрока (isBlocked удалял pending и возвращал false),
 * делая второй фактор необязательным.
 */
class GateListenerTest {

    private static final long CHAT_ID = 777L;

    private MockedStatic<Bukkit> bukkit;
    private BindingsStore store;
    private TrustStore trust;
    private TelegramApi tg;
    private Player player;
    private UUID playerId;

    @BeforeEach
    void setUpBukkit() {
        bukkit = mockStatic(Bukkit.class);

        // Планировщик: async/sync задачи выполняем немедленно — тест детерминированный.
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskAsynchronously(any(), any(Runnable.class)))
                .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        when(scheduler.runTask(any(), any(Runnable.class)))
                .thenAnswer(inv -> { inv.getArgument(1, Runnable.class).run(); return null; });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

        // Fingerprints спрашивает у PluginManager про floodgate.
        PluginManager pm = mock(PluginManager.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pm);

        store = mock(BindingsStore.class);
        trust = mock(TrustStore.class);
        tg = mock(TelegramApi.class);

        player = mock(Player.class);
        playerId = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getName()).thenReturn("Steve");
        when(player.isOnline()).thenReturn(true);
        when(player.getAddress()).thenReturn(new InetSocketAddress("203.0.113.7", 25565));
    }

    @AfterEach
    void tearDownBukkit() {
        bukkit.close();
    }

    private GateListener gate(int codeTtlSeconds) {
        SecurityConfig sec = new SecurityConfig(codeTtlSeconds, false, "taipan.authtg.bypass", 3600, "none", 24);
        PluginConfig cfg = new PluginConfig("yaml", null, sec, null);
        return new GateListener(mock(JavaPlugin.class), cfg, store, trust, tg);
    }

    @Test
    void expiredCodeKeepsPlayerFrozen() {
        // AUTH-7: игрок вошёл в AuthMe, код НЕ ввёл, TTL истёк → всё ещё заморожен.
        when(store.getChatIdByNick("Steve")).thenReturn(CHAT_ID);
        GateListener gate = gate(-1); // код «истёк» сразу после выдачи

        gate.onAuthMeLogin(player, true);

        // Симулируем поток событий: гейт опрашивается многократно уже после истечения TTL.
        for (int i = 0; i < 3; i++) {
            assertTrue(gate.mustBeFrozen(player), "истёкший код не должен открывать гейт");
            assertFalse(gate.isFullyAuthorized(player));
            gate.isBlocked(player);
        }

        // И истёкший код не принимается.
        assertFalse(gate.tryAcceptCode(player, "000000"));
        assertTrue(gate.mustBeFrozen(player));
    }

    @Test
    void acceptedCodeOpensGate() {
        when(store.getChatIdByNick("Steve")).thenReturn(CHAT_ID);
        GateListener gate = gate(120);

        gate.onAuthMeLogin(player, true);
        assertTrue(gate.mustBeFrozen(player), "до ввода кода игрок заморожен");

        String code = sentCode();
        assertTrue(gate.tryAcceptCode(player, code));
        assertTrue(gate.isFullyAuthorized(player));
        assertFalse(gate.mustBeFrozen(player));
    }

    @Test
    void wrongCodeDoesNotOpenGateAndLocksAfterMaxAttempts() {
        when(store.getChatIdByNick("Steve")).thenReturn(CHAT_ID);
        GateListener gate = gate(120);

        gate.onAuthMeLogin(player, true);
        String realCode = sentCode();

        for (int i = 0; i < 5; i++) {
            assertFalse(gate.tryAcceptCode(player, "999999"));
        }

        // AUTH-2: после исчерпания попыток даже верный код не принимается.
        assertFalse(gate.tryAcceptCode(player, realCode));
        assertTrue(gate.mustBeFrozen(player));
    }

    @Test
    void validTrustSessionSkipsCode() {
        // trustBind=none → отпечаток не зависит от IP.
        String fp = "java|none";
        when(trust.getValid(playerId))
                .thenReturn(new TrustSession(playerId, fp, Instant.now().plusSeconds(600)));
        GateListener gate = gate(120);

        gate.onAuthMeLogin(player, true);

        assertTrue(gate.isFullyAuthorized(player));
        assertFalse(gate.mustBeFrozen(player));
    }

    @Test
    void authMeLogoutClosesGateAgain() {
        when(store.getChatIdByNick("Steve")).thenReturn(CHAT_ID);
        GateListener gate = gate(120);

        gate.onAuthMeLogin(player, true);
        assertTrue(gate.tryAcceptCode(player, sentCode()));
        assertFalse(gate.mustBeFrozen(player));

        gate.onAuthMeLogin(player, false);
        assertTrue(gate.mustBeFrozen(player), "после выхода из AuthMe гейт снова закрыт");
    }

    @Test
    void missingTelegramBindingBlocksPlayer() {
        when(store.getChatIdByNick("Steve")).thenReturn(null);
        GateListener gate = gate(120);

        gate.onAuthMeLogin(player, true);

        assertTrue(gate.isBlockedNoTg(player));
        assertTrue(gate.mustBeFrozen(player));
        assertFalse(gate.tryAcceptCode(player, "123456"));
    }

    /** Достаёт код из сообщения, отправленного в Telegram. */
    private String sentCode() {
        ArgumentCaptor<String> msg = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(tg).sendMessage(anyLong(), msg.capture(), any());
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d{6})\\b").matcher(msg.getValue());
        assertTrue(m.find(), "в сообщении должен быть 6-значный код: " + msg.getValue());
        return m.group(1);
    }
}
