package dev.taipan.astronexis.core.module.menu;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.bootstrap.ServiceRegistry;
import dev.taipan.astronexis.core.module.CoreModule;
import dev.taipan.astronexis.core.module.menu.listener.ActionBarListener;
import dev.taipan.astronexis.core.module.menu.listener.CompassListener;
import dev.taipan.astronexis.core.module.menu.listener.CompassProtectListener;
import dev.taipan.astronexis.core.module.menu.listener.HubCompassGiver;
import dev.taipan.astronexis.core.module.menu.listener.MenuListener;
import dev.taipan.astronexis.core.module.menu.store.LastLocationStore;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.List;

public final class MenuModule implements CoreModule {

    private final AstronexisCorePlugin plugin;
    private final ServiceRegistry services;

    private LastLocationStore lastLocationStore;
    private MenuService menuService;
    private ActionBarService actionBarService;

    public MenuModule(AstronexisCorePlugin plugin, ServiceRegistry services) {
        this.plugin = plugin;
        this.services = services;
    }

    @Override
    public String id() {
        return "menu";
    }

    @Override
    public void enable() {
        this.lastLocationStore = new LastLocationStore(plugin);
        this.lastLocationStore.load();

        this.menuService = new MenuService(plugin, services, lastLocationStore);
        this.actionBarService = new ActionBarService(plugin, services);

        register(new MenuListener(menuService));
        register(new CompassListener(menuService));
        register(new CompassProtectListener(menuService));
        register(new HubCompassGiver(plugin, menuService));
        register(lastLocationStore);
        register(new ActionBarListener(actionBarService));

        if (plugin.getCommand("menu") != null) {
            plugin.getCommand("menu").setExecutor(new MenuCommand(menuService, services.messages()));
        }

        actionBarService.start();
    }

    @Override
    public void disable() {
        if (actionBarService != null) {
            actionBarService.stop();
        }

        if (lastLocationStore != null) {
            lastLocationStore.save();
        }
    }

    private void register(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public static final class ActionBarService {

        private final AstronexisCorePlugin plugin;
        private final ServiceRegistry services;
        private int taskId = -1;

        public ActionBarService(AstronexisCorePlugin plugin, ServiceRegistry services) {
            this.plugin = plugin;
            this.services = services;
        }

        public void start() {
            stop();

            FileConfiguration cfg = plugin.getConfig();
            if (!cfg.getBoolean("actionbar.enabled", false)) {
                return;
            }

            long interval = cfg.getLong("actionbar.intervalTicks", 40L);
            if (interval < 1L) {
                interval = 40L;
            }

            taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                    plugin,
                    this::tickAll,
                    20L,
                    interval
            );
        }

        public void stop() {
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
            }
        }

        public void updateNow(Player player) {
            if (player == null || !player.isOnline()) {
                return;
            }

            FileConfiguration cfg = plugin.getConfig();
            if (!cfg.getBoolean("actionbar.enabled", false)) {
                return;
            }

            boolean isBedrock = services.platformService().isBedrock(player);
            if (isBedrock && !cfg.getBoolean("actionbar.enabledForBedrock", true)) {
                return;
            }
            if (!isBedrock && !cfg.getBoolean("actionbar.enabledForJava", true)) {
                return;
            }

            if (!shouldShow(player, cfg)) {
                return;
            }

            String text = cfg.getString("actionbar.text", "&dmc.astronexis.site");
            text = text
                    .replace("%player%", player.getName())
                    .replace("%world%", player.getWorld() != null ? player.getWorld().getName() : "unknown")
                    .replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));

            player.sendActionBar(text.replace("&", "§"));
        }

        private void tickAll() {
            for (Player player : Bukkit.getOnlinePlayers()) {
                updateNow(player);
            }
        }

        private boolean shouldShow(Player player, FileConfiguration cfg) {
            List<String> worlds = cfg.getStringList("actionbar.worlds");
            if (worlds == null || worlds.isEmpty()) {
                return true;
            }

            String current = player.getWorld() != null ? player.getWorld().getName() : null;
            if (current == null) {
                return false;
            }

            for (String worldName : worlds) {
                if (worldName != null && worldName.equalsIgnoreCase(current)) {
                    return true;
                }
            }
            return false;
        }
    }
}