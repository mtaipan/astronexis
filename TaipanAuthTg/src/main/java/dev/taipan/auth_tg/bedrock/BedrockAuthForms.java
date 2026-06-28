package dev.taipan.auth_tg.bedrock;

import dev.taipan.auth_tg.gate.GateListener;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.cumulus.form.CustomForm;

public final class BedrockAuthForms {

    private final JavaPlugin plugin;
    private final GateListener gate;

    public BedrockAuthForms(JavaPlugin plugin, GateListener gate) {
        this.plugin = plugin;
        this.gate = gate;
    }

    public boolean isBedrock(Player p) {
        try {
            return FloodgateApi.getInstance().isFloodgatePlayer(p.getUniqueId());
        } catch (Throwable t) {
            return false;
        }
    }

    public void openMain(Player p) {
        FloodgatePlayer fp = getFloodgatePlayer(p);
        if (fp == null) return;

        fp.sendForm(
                SimpleForm.builder()
                        .title("Авторизация")
                        .content("Выбери действие")
                        .button("Войти")
                        .button("Регистрация")
                        .button("Telegram код")
                        .validResultHandler(response -> {
                            int id = response.clickedButtonId();
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player live = Bukkit.getPlayer(p.getUniqueId());
                                if (live == null) return;

                                if (id == 0) openLogin(live);
                                else if (id == 1) openRegister(live);
                                else if (id == 2) openTgCode(live);
                            });
                        })
        );
    }

    private void openLogin(Player p) {
        FloodgatePlayer fp = getFloodgatePlayer(p);
        if (fp == null) return;

        fp.sendForm(
                CustomForm.builder()
                        .title("Вход")
                        .input("Пароль", "введи пароль", "")
                        .validResultHandler(response -> {
                            String pass = response.next();

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player live = Bukkit.getPlayer(p.getUniqueId());
                                if (live == null) return;

                                String password = (pass == null ? "" : pass).trim();
                                if (password.isEmpty()) {
                                    live.sendMessage(ChatColor.RED + "Пароль пустой.");
                                    reopenMainSoon(live, 1);
                                    return;
                                }

                                live.performCommand("login " + password);

                                // После /login форма закрылась => переоткрываем, пока не авторизовался
                                reopenAfterAuthCommand(live, 12L, 2);
                            });
                        })
                        .closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            Player live = Bukkit.getPlayer(p.getUniqueId());
                            if (live != null && !gate.isFullyAuthorized(live)) openMain(live);
                        }))
        );
    }

    private void openRegister(Player p) {
        FloodgatePlayer fp = getFloodgatePlayer(p);
        if (fp == null) return;

        fp.sendForm(
                CustomForm.builder()
                        .title("Регистрация")
                        .input("Пароль", "введи пароль", "")
                        .input("Повтори пароль", "повтори пароль", "")
                        .validResultHandler(response -> {
                            final String raw1 = response.next();
                            final String raw2 = response.next();

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player live = Bukkit.getPlayer(p.getUniqueId());
                                if (live == null) return;

                                final String pass1 = (raw1 == null ? "" : raw1).trim();
                                final String pass2 = (raw2 == null ? "" : raw2).trim();

                                if (pass1.isEmpty() || pass2.isEmpty()) {
                                    live.sendMessage(ChatColor.RED + "Пароль пустой.");
                                    reopenMainSoon(live, 1);
                                    return;
                                }

                                if (!pass1.equals(pass2)) {
                                    live.sendMessage(ChatColor.RED + "Пароли не совпадают.");
                                    // снова открыть регистрацию
                                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                        Player l2 = Bukkit.getPlayer(live.getUniqueId());
                                        if (l2 != null && !gate.isFullyAuthorized(l2)) openRegister(l2);
                                    }, 10L);
                                    return;
                                }

                                live.performCommand("register " + pass1 + " " + pass2);

                                // После /register форма закрылась => переоткрываем, пока не авторизовался
                                reopenAfterAuthCommand(live, 12L, 2);
                            });
                        })
                        .closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            Player live = Bukkit.getPlayer(p.getUniqueId());
                            if (live != null && !gate.isFullyAuthorized(live)) openMain(live);
                        }))
        );
    }

    private void openTgCode(Player p) {
        FloodgatePlayer fp = getFloodgatePlayer(p);
        if (fp == null) return;

        fp.sendForm(
                CustomForm.builder()
                        .title("Telegram 2FA")
                        .input("Код", "6 цифр", "")
                        .validResultHandler(response -> {
                            String code = response.next();

                            Bukkit.getScheduler().runTask(plugin, () -> {
                                Player live = Bukkit.getPlayer(p.getUniqueId());
                                if (live == null) return;

                                String c = (code == null ? "" : code).trim();
                                if (c.isEmpty()) {
                                    live.sendMessage(ChatColor.RED + "Код пустой.");
                                    reopenTgSoon(live, 1);
                                    return;
                                }

                                live.performCommand("tgcode " + c);

                                // Если код неверный — GateListener оставит блок, и мы откроем форму снова
                                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                    Player l2 = Bukkit.getPlayer(live.getUniqueId());
                                    if (l2 == null) return;
                                    if (gate.isFullyAuthorized(l2)) return;

                                    // Всё ещё заблокирован -> снова просим код
                                    if (gate.isBlocked(l2)) openTgCode(l2);
                                    else openMain(l2);
                                }, 10L);
                            });
                        })
                        .closedOrInvalidResultHandler(() -> Bukkit.getScheduler().runTask(plugin, () -> {
                            Player live = Bukkit.getPlayer(p.getUniqueId());
                            if (live != null && !gate.isFullyAuthorized(live)) openMain(live);
                        }))
        );
    }

    /**
     * После /login или /register:
     * - ждём немного (poller/AuthMe может обновиться не сразу)
     * - если требуется 2FA -> открываем TG
     * - иначе -> главное меню
     * - повторяем несколько раз (attempts), чтобы покрыть задержку Poller-а
     */
    private void reopenAfterAuthCommand(Player p, long delayTicks, int attempts) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player live = Bukkit.getPlayer(p.getUniqueId());
            if (live == null) return;

            if (gate.isFullyAuthorized(live)) return;

            // если уже залогинен и есть блок 2FA — сразу просим код
            if (gate.isAuthMeLoggedIn(live) && gate.isBlocked(live)) {
                openTgCode(live);
                return;
            }

            // иначе возвращаем в меню
            openMain(live);

            // ещё попытка, если poller не успел
            if (attempts > 1) reopenAfterAuthCommand(live, 20L, attempts - 1);
        }, delayTicks);
    }

    private void reopenMainSoon(Player p, int attempts) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player live = Bukkit.getPlayer(p.getUniqueId());
            if (live == null) return;
            if (gate.isFullyAuthorized(live)) return;
            openMain(live);
            if (attempts > 1) reopenMainSoon(live, attempts - 1);
        }, 8L);
    }

    private void reopenTgSoon(Player p, int attempts) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player live = Bukkit.getPlayer(p.getUniqueId());
            if (live == null) return;
            if (gate.isFullyAuthorized(live)) return;
            openTgCode(live);
            if (attempts > 1) reopenTgSoon(live, attempts - 1);
        }, 8L);
    }

    private FloodgatePlayer getFloodgatePlayer(Player p) {
        try {
            FloodgateApi api = FloodgateApi.getInstance();
            return api.getPlayer(p.getUniqueId());
        } catch (Throwable t) {
            return null;
        }
    }
}