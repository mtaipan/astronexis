package dev.taipan.astronexis.core.module.menu;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.bootstrap.ServiceRegistry;
import dev.taipan.astronexis.core.module.menu.store.LastLocationStore;
import dev.taipan.astronexis.core.module.menu.ui.BedrockMenuRenderer;
import dev.taipan.astronexis.core.module.menu.ui.JavaMenuRenderer;
import dev.taipan.astronexis.core.shared.i18n.MessageService;
import dev.taipan.astronexis.core.shared.platform.PlatformService;
import dev.taipan.astronexis.core.shared.text.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MenuService {

    private final AstronexisCorePlugin plugin;
    private final MessageService messages;
    private final PlatformService platformService;
    private final LastLocationStore lastLocationStore;
    private final JavaMenuRenderer javaRenderer;
    private final BedrockMenuRenderer bedrockRenderer;

    private final NamespacedKey compassKey;
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    public MenuService(AstronexisCorePlugin plugin, ServiceRegistry services, LastLocationStore lastLocationStore) {
        this.plugin = plugin;
        this.messages = services.messages();
        this.platformService = services.platformService();
        this.lastLocationStore = lastLocationStore;
        this.javaRenderer = new JavaMenuRenderer(plugin, services.messages());
        this.bedrockRenderer = new BedrockMenuRenderer(plugin, services.messages());
        this.compassKey = new NamespacedKey(plugin, "menu_compass");
    }

    public void open(Player player, MenuPage page) {
        if (platformService.isBedrock(player)) {
            bedrockRenderer.open(player, page);
        } else {
            javaRenderer.open(player, page);
        }
    }

    public boolean isOurMenu(org.bukkit.inventory.Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof MenuHolder;
    }

    public MenuPage getPage(org.bukkit.inventory.Inventory inventory) {
        if (!(inventory.getHolder() instanceof MenuHolder holder)) {
            return null;
        }
        return holder.page();
    }

    public boolean isBedrockLayout(org.bukkit.inventory.Inventory inventory) {
        if (!(inventory.getHolder() instanceof MenuHolder holder)) {
            return false;
        }
        return holder.bedrockLayout();
    }

    public boolean allowClick(Player player) {
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("menu.clickCooldownMs", 800L);

        Long last = clickCooldown.get(player.getUniqueId());
        if (last != null && now - last < cd) {
            player.sendMessage(messages.get(player, "message.menu-cooldown"));
            return false;
        }

        clickCooldown.put(player.getUniqueId(), now);
        return true;
    }

    public void handleClick(Player player, int rawSlot, MenuPage page) {
        if (!allowClick(player)) {
            return;
        }

        switch (page) {
            case MAIN -> handleMain(player, rawSlot);
            case HELP -> handleHelp(player, rawSlot);
            case HELP_PRIVATES -> handleHelpPrivates(player, rawSlot);
            case HELP_COMMANDS -> handleHelpCommands(player, rawSlot);
        }
    }

    private void handleMain(Player player, int slot) {
        switch (slot) {
            case 11, 2 -> {
                teleportToSurvivalLastOrSpawn(player);
                player.closeInventory();
            }
            case 13, 4 -> {
                teleportToHub(player);
                player.closeInventory();
            }
            case 15, 6 -> open(player, MenuPage.HELP);
            case 8 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleHelp(Player player, int slot) {
        switch (slot) {
            case 11, 2 -> open(player, MenuPage.HELP_PRIVATES);
            case 13, 4 -> open(player, MenuPage.HELP_COMMANDS);
            case 15, 6 -> open(player, MenuPage.MAIN);
            case 8 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleHelpPrivates(Player player, int slot) {
        switch (slot) {
            case 11, 3 -> sendHelpPrivates(player);
            case 15, 5 -> open(player, MenuPage.HELP);
            case 8 -> player.closeInventory();
            default -> {
            }
        }
    }

    private void handleHelpCommands(Player player, int slot) {
        switch (slot) {
            case 11, 3 -> sendHelpCommands(player);
            case 15, 5 -> open(player, MenuPage.HELP);
            case 8 -> player.closeInventory();
            default -> {
            }
        }
    }

    public void teleportToSurvivalLastOrSpawn(Player player) {
        LastLocationStore.StoredLoc last = lastLocationStore.getLastSurvivalLocation(player.getUniqueId());
        if (last != null) {
            World world = Bukkit.getWorld(last.worldName());
            if (world != null) {
                player.teleport(last.toBukkitLocation(world));
                player.sendMessage(messages.get(player, "message.teleported-survival-last"));
                return;
            }
        }

        String fallbackName = plugin.getConfig().getString("worlds.survivalFallback", "world");
        World fallback = Bukkit.getWorld(fallbackName);
        if (fallback == null) {
            player.sendMessage(messages.get(player, "message.survival-world-not-found"));
            return;
        }

        player.teleport(fallback.getSpawnLocation().add(0.5, 0, 0.5));
        player.sendMessage(messages.get(player, "message.teleported-survival-spawn"));
    }

    public void teleportToHub(Player player) {
        String hubName = plugin.getConfig().getString("worlds.hub", "hub");
        World hub = Bukkit.getWorld(hubName);
        if (hub == null) {
            player.sendMessage(messages.format(player, "message.hub-world-not-found", Map.of("world", hubName)));
            return;
        }

        player.teleport(hub.getSpawnLocation().add(0.5, 0, 0.5));
        player.sendMessage(messages.get(player, "message.teleported-hub"));
    }

    public void sendHelpPrivates(Player player) {
        for (String line : messages.getList(player, "help.privates")) {
            player.sendMessage(line);
        }
    }

    public void sendHelpCommands(Player player) {
        for (String line : messages.getList(player, "help.commands")) {
            player.sendMessage(line);
        }
    }

    public ItemStack createHubCompass() {
        FileConfiguration cfg = plugin.getConfig();

        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return it;
        }

        meta.setDisplayName(Texts.color(cfg.getString("compass.name", "&bМеню")));

        List<String> lore = cfg.getStringList("compass.lore");
        if (lore != null && !lore.isEmpty()) {
            meta.setLore(lore.stream().map(Texts::color).toList());
        }

        meta.getPersistentDataContainer().set(compassKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    public boolean isOurCompass(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS) {
            return false;
        }

        ItemMeta meta = it.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte value = meta.getPersistentDataContainer().get(compassKey, PersistentDataType.BYTE);
        return value != null && value == 1;
    }
}