package dev.taipan.astronexis.core.module.menu.listener;

import dev.taipan.astronexis.core.module.menu.MenuPage;
import dev.taipan.astronexis.core.module.menu.MenuService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

public final class CompassListener implements Listener {

    private final MenuService menuService;

    public CompassListener(MenuService menuService) {
        this.menuService = menuService;
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        if (event.getItem() == null) {
            return;
        }

        if (!menuService.isOurCompass(event.getItem())) {
            return;
        }

        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
                menuService.open(event.getPlayer(), MenuPage.MAIN);
                event.setCancelled(true);
            }
            default -> {
            }
        }
    }
}