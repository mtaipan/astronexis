package dev.taipan.astronexis.core.module.menu.store;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LastLocationStore implements Listener {

    public record StoredLoc(String worldName, double x, double y, double z, float yaw, float pitch) {
        public Location toBukkitLocation(org.bukkit.World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }
    }

    private final AstronexisCorePlugin plugin;
    private final File file;
    private YamlConfiguration yaml;

    private final Map<UUID, StoredLoc> lastSurvival = new HashMap<>();

    public LastLocationStore(AstronexisCorePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.lastSurvival.clear();

        var section = yaml.getConfigurationSection("lastSurvival");
        if (section == null) {
            return;
        }

        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String world = section.getString(key + ".world");
                double x = section.getDouble(key + ".x");
                double y = section.getDouble(key + ".y");
                double z = section.getDouble(key + ".z");
                float yaw = (float) section.getDouble(key + ".yaw");
                float pitch = (float) section.getDouble(key + ".pitch");

                if (world != null && !world.isBlank()) {
                    lastSurvival.put(uuid, new StoredLoc(world, x, y, z, yaw, pitch));
                }
            } catch (Exception ignored) {
            }
        }
    }

    public void save() {
        if (yaml == null) {
            yaml = new YamlConfiguration();
        }

        yaml.set("lastSurvival", null);

        for (Map.Entry<UUID, StoredLoc> entry : lastSurvival.entrySet()) {
            String base = "lastSurvival." + entry.getKey();
            StoredLoc loc = entry.getValue();

            yaml.set(base + ".world", loc.worldName());
            yaml.set(base + ".x", loc.x());
            yaml.set(base + ".y", loc.y());
            yaml.set(base + ".z", loc.z());
            yaml.set(base + ".yaw", loc.yaw());
            yaml.set(base + ".pitch", loc.pitch());
        }

        try {
            yaml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save data.yml: " + ex.getMessage());
        }
    }

    public StoredLoc getLastSurvivalLocation(UUID uuid) {
        return lastSurvival.get(uuid);
    }

    private boolean isSurvivalWorld(String worldName) {
        List<String> worlds = plugin.getConfig().getStringList("worlds.survivalWorlds");
        if (worlds == null || worlds.isEmpty()) {
            return "world".equalsIgnoreCase(worldName)
                    || "world_nether".equalsIgnoreCase(worldName)
                    || "world_the_end".equalsIgnoreCase(worldName);
        }

        for (String world : worlds) {
            if (world != null && world.equalsIgnoreCase(worldName)) {
                return true;
            }
        }
        return false;
    }

    private void saveLocation(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) {
            return;
        }

        String worldName = location.getWorld().getName();
        if (!isSurvivalWorld(worldName)) {
            return;
        }

        lastSurvival.put(player.getUniqueId(), new StoredLoc(
                worldName,
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        ));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        saveLocation(event.getPlayer(), event.getPlayer().getLocation());
        save();
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getFrom() != null && event.getFrom().getWorld() != null) {
            saveLocation(event.getPlayer(), event.getFrom());
        }
    }
}