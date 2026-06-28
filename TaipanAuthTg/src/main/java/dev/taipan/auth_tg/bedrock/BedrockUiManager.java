package dev.taipan.auth_tg.bedrock;

import dev.taipan.auth_tg.gate.GateListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BedrockUiManager implements Listener {

    private final JavaPlugin plugin;
    private final GateListener gate;
    private final BedrockAuthForms forms;

    private final Map<UUID, BukkitTask> reminders = new ConcurrentHashMap<>();

    public BedrockUiManager(JavaPlugin plugin, GateListener gate, BedrockAuthForms forms) {
        this.plugin = plugin;
        this.gate = gate;
        this.forms = forms;
    }

    public void openFor(Player p) {
        if (forms.isBedrock(p)) {
            // Bedrock: открываем формы
            if (p.isOnline()) {
                Bukkit.getScheduler().runTask(plugin, () -> forms.openMain(p));
            }
            return;
        }

        // Java: просто подсказка (без GUI)
        sendJavaHelp(p);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // Авто-открытие форм для Bedrock
        if (forms.isBedrock(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!p.isOnline()) return;
                if (gate.isFullyAuthorized(p)) return;
                forms.openMain(p);
            }, 20L);
        }

        startReminder(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        stopReminder(e.getPlayer());
    }

    private void startReminder(Player p) {
        stopReminder(p);

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) { stopReminder(p); return; }
            if (gate.isFullyAuthorized(p)) { stopReminder(p); return; }

            if (!gate.isAuthMeLoggedIn(p)) {
                // Ещё даже не /login
                if (forms.isBedrock(p)) {
                    p.sendMessage(ChatColor.GRAY + "Авторизация: открой форму командой " + ChatColor.YELLOW + "/auth");
                    p.sendMessage(ChatColor.GRAY + "Или используй " + ChatColor.YELLOW + "/login <пароль>" + ChatColor.GRAY + " / " + ChatColor.YELLOW + "/register <пароль> <пароль>");
                } else {
                    sendJavaHelp(p);
                }
                return;
            }

            // AuthMe ок, но 2FA ещё не пройден
            if (gate.isBlockedNoTg(p)) {
                p.sendMessage(ChatColor.RED + "Привяжи Telegram: напиши боту /bind <ник>, затем снова /login.");
                if (forms.isBedrock(p)) p.sendMessage(ChatColor.GRAY + "Форма: " + ChatColor.YELLOW + "/auth");
                return;
            }

            if (gate.isBlocked(p)) {
                p.sendMessage(ChatColor.YELLOW + "2FA: проверь Telegram и введи код.");
                if (forms.isBedrock(p)) {
                    p.sendMessage(ChatColor.GRAY + "Форма: " + ChatColor.YELLOW + "/auth");
                } else {
                    p.sendMessage(ChatColor.GRAY + "Команда: " + ChatColor.YELLOW + "/tgcode <код>");
                }
            }
        }, 40L, 100L); // 2 сек после входа, далее каждые 5 сек

        reminders.put(p.getUniqueId(), task);
    }

    private void stopReminder(Player p) {
        BukkitTask t = reminders.remove(p.getUniqueId());
        if (t != null) t.cancel();
    }

    private static void sendJavaHelp(Player p) {
        p.sendMessage(ChatColor.GRAY + "Авторизация (Java):");
        p.sendMessage(ChatColor.YELLOW + "/login <пароль>");
        p.sendMessage(ChatColor.YELLOW + "/register <пароль> <пароль>");
        p.sendMessage(ChatColor.GRAY + "2FA (после логина): " + ChatColor.YELLOW + "/tgcode <код>");
    }
}