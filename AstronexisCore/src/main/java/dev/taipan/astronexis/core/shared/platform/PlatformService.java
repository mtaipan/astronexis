package dev.taipan.astronexis.core.shared.platform;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.UUID;

public final class PlatformService {

    private final AstronexisCorePlugin plugin;

    private boolean floodgateAvailable;
    private Method floodgateGetInstance;
    private Method floodgateIsPlayer;

    public PlatformService(AstronexisCorePlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        this.floodgateAvailable = false;
        this.floodgateGetInstance = null;
        this.floodgateIsPlayer = null;

        Plugin floodgatePlugin = plugin.getServer().getPluginManager().getPlugin("floodgate");
        if (floodgatePlugin == null) {
            plugin.getLogger().info("Floodgate not found. Bedrock detection disabled.");
            return;
        }

        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            this.floodgateGetInstance = apiClass.getMethod("getInstance");
            this.floodgateIsPlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            this.floodgateAvailable = true;
            plugin.getLogger().info("Floodgate detected. Bedrock-aware menu enabled.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Floodgate found, but API hook failed: " + ex.getMessage());
        }
    }

    public boolean isBedrock(Player player) {
        if (player == null || !floodgateAvailable || floodgateGetInstance == null || floodgateIsPlayer == null) {
            return false;
        }

        try {
            Object api = floodgateGetInstance.invoke(null);
            Object result = floodgateIsPlayer.invoke(api, player.getUniqueId());
            return result instanceof Boolean b && b;
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean isJava(Player player) {
        return !isBedrock(player);
    }
}