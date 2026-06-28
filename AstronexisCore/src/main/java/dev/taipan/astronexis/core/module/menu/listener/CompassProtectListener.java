package dev.taipan.astronexis.core.module.menu.listener;

import dev.taipan.astronexis.core.module.menu.MenuService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class CompassProtectListener implements Listener {

    private final MenuService menuService;

    public CompassProtectListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (menuService.isOurCompass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if ((event.getMainHandItem() != null && menuService.isOurCompass(event.getMainHandItem()))
                || (event.getOffHandItem() != null && menuService.isOurCompass(event.getOffHandItem()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() != null && menuService.isOurCompass(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        if (event.getCursor() != null && menuService.isOurCompass(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getOldCursor() != null && menuService.isOurCompass(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }
}