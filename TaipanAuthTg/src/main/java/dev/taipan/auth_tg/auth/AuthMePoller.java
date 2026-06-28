package dev.taipan.auth_tg.auth;

import dev.taipan.auth_tg.gate.GateListener;
import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AuthMePoller {

    private final JavaPlugin plugin;
    private final GateListener gate;
    private int taskId = -1;

    private final Map<UUID, Boolean> last = new HashMap<>();

    public AuthMePoller(JavaPlugin plugin, GateListener gate) {
        this.plugin = plugin;
        this.gate = gate;
    }

    public void start() {
        stop();
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (taskId != -1) Bukkit.getScheduler().cancelTask(taskId);
        taskId = -1;
        last.clear();
    }

    private void tick() {
        AuthMeApi api;
        try {
            api = AuthMeApi.getInstance();
        } catch (Throwable t) {
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean loggedIn = api.isAuthenticated(p);

            Boolean prev = last.put(p.getUniqueId(), loggedIn);
            if (prev == null) prev = false;

            if (!prev && loggedIn) {
                gate.onAuthMeLogin(p, true);
            } else if (prev && !loggedIn) {
                gate.onAuthMeLogin(p, false);
            }
        }
    }
}