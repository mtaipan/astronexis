package dev.taipan.astronexis.core.module.menu.listener;

import dev.taipan.astronexis.core.module.menu.MenuPage;
import dev.taipan.astronexis.core.module.menu.MenuService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public final class MenuListener implements Listener {

    private final MenuService menuService;

    public MenuListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!menuService.isOurMenu(event.getInventory())) {
            return;
        }

        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getInventory().getSize()) {
            return;
        }

        MenuPage page = menuService.getPage(event.getInventory());
        if (page == null) {
            return;
        }

        menuService.handleClick(player, rawSlot, page);
    }
}