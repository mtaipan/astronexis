package dev.taipan.astronexis.core.module.menu.listener;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.module.menu.MenuService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public final class HubCompassGiver implements Listener {

    private final AstronexisCorePlugin plugin;
    private final MenuService menuService;

    public HubCompassGiver(AstronexisCorePlugin plugin, MenuService menuService) {
        this.plugin = plugin;
        this.menuService = menuService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureCompassIfNeeded(event.getPlayer()), 20L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> ensureCompassIfNeeded(event.getPlayer()), 1L);
    }

    private void ensureCompassIfNeeded(Player player) {
        if (!plugin.getConfig().getBoolean("compass.enabled", true)) {
            return;
        }

        boolean onlyInHub = plugin.getConfig().getBoolean("compass.onlyInHub", true);
        String hubName = plugin.getConfig().getString("worlds.hub", "hub");

        World world = player.getWorld();
        if (world == null) {
            return;
        }

        if (onlyInHub && !world.getName().equalsIgnoreCase(hubName)) {
            return;
        }

        if (hasOurCompass(player)) {
            return;
        }

        ItemStack compass = menuService.createHubCompass();
        int slot = plugin.getConfig().getInt("compass.slot", 0);

        if (slot < 0 || slot > 8) {
            slot = 0;
        }

        ItemStack current = player.getInventory().getItem(slot);
        if (current == null || current.getType().isAir()) {
            player.getInventory().setItem(slot, compass);
        } else {
            player.getInventory().addItem(compass);
        }
    }

    private boolean hasOurCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (menuService.isOurCompass(item)) {
                return true;
            }
        }
        return false;
    }
}