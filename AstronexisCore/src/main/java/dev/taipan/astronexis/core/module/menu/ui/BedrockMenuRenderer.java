package dev.taipan.astronexis.core.module.menu.ui;

import dev.taipan.astronexis.core.AstronexisCorePlugin;
import dev.taipan.astronexis.core.module.menu.MenuHolder;
import dev.taipan.astronexis.core.module.menu.MenuPage;
import dev.taipan.astronexis.core.shared.i18n.MessageService;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public final class BedrockMenuRenderer {

    private final AstronexisCorePlugin plugin;
    private final MessageService messages;

    public BedrockMenuRenderer(AstronexisCorePlugin plugin, MessageService messages) {
        this.plugin = plugin;
        this.messages = messages;
    }

    public void open(Player player, MenuPage page) {
        String title = switch (page) {
            case MAIN -> messages.get(player, "menu.title-main-bedrock");
            case HELP -> messages.get(player, "menu.title-help-bedrock");
            case HELP_PRIVATES -> messages.get(player, "menu.title-help-privates");
            case HELP_COMMANDS -> messages.get(player, "menu.title-help-commands");
        };

        Inventory inv = Bukkit.createInventory(new MenuHolder(page, true), 9, title);
        fill(inv);

        switch (page) {
            case MAIN -> buildMain(player, inv);
            case HELP -> buildHelp(player, inv);
            case HELP_PRIVATES -> buildHelpPrivates(player, inv);
            case HELP_COMMANDS -> buildHelpCommands(player, inv);
        }

        player.openInventory(inv);
    }

    private void buildMain(Player player, Inventory inv) {
        inv.setItem(2, item(
                Material.GRASS_BLOCK,
                messages.get(player, "menu.item-survival"),
                List.of(messages.get(player, "menu.item-survival-lore-1"))
        ));

        inv.setItem(4, item(
                Material.COMPASS,
                messages.get(player, "menu.item-hub"),
                List.of(messages.get(player, "menu.item-hub-lore-1"))
        ));

        inv.setItem(6, item(
                Material.BOOK,
                messages.get(player, "menu.item-help"),
                List.of(messages.get(player, "menu.item-help-lore-1"))
        ));

        inv.setItem(8, item(
                Material.BARRIER,
                messages.get(player, "menu.item-close"),
                List.of(messages.get(player, "menu.item-close-lore-1"))
        ));
    }

    private void buildHelp(Player player, Inventory inv) {
        inv.setItem(2, item(
                Material.GOLDEN_AXE,
                messages.get(player, "menu.item-privates"),
                List.of(messages.get(player, "menu.item-privates-lore-1"))
        ));

        inv.setItem(4, item(
                Material.PAPER,
                messages.get(player, "menu.item-commands"),
                List.of(messages.get(player, "menu.item-commands-lore-1"))
        ));

        inv.setItem(6, item(
                Material.ARROW,
                messages.get(player, "menu.item-back"),
                List.of(messages.get(player, "menu.item-back-lore-1"))
        ));

        inv.setItem(8, item(
                Material.BARRIER,
                messages.get(player, "menu.item-close"),
                List.of(messages.get(player, "menu.item-close-lore-1"))
        ));
    }

    private void buildHelpPrivates(Player player, Inventory inv) {
        inv.setItem(3, item(
                Material.GOLDEN_AXE,
                messages.get(player, "menu.item-show-chat"),
                List.of(messages.get(player, "menu.item-show-chat-lore-1"))
        ));

        inv.setItem(5, item(
                Material.ARROW,
                messages.get(player, "menu.item-back"),
                List.of(messages.get(player, "menu.item-back-lore-1"))
        ));

        inv.setItem(8, item(
                Material.BARRIER,
                messages.get(player, "menu.item-close"),
                List.of(messages.get(player, "menu.item-close-lore-1"))
        ));
    }

    private void buildHelpCommands(Player player, Inventory inv) {
        inv.setItem(3, item(
                Material.PAPER,
                messages.get(player, "menu.item-show-chat"),
                List.of(messages.get(player, "menu.item-show-chat-lore-1"))
        ));

        inv.setItem(5, item(
                Material.ARROW,
                messages.get(player, "menu.item-back"),
                List.of(messages.get(player, "menu.item-back-lore-1"))
        ));

        inv.setItem(8, item(
                Material.BARRIER,
                messages.get(player, "menu.item-close"),
                List.of(messages.get(player, "menu.item-close-lore-1"))
        ));
    }

    private void fill(Inventory inv) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            filler.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            it.setItemMeta(meta);
        }
        return it;
    }
}