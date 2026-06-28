package dev.taipan.astronexis.core.module.menu.listener;

import dev.taipan.astronexis.core.module.menu.MenuModule;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public final class ActionBarListener implements Listener {

    private final MenuModule.ActionBarService actionBarService;

    public ActionBarListener(MenuModule.ActionBarService actionBarService) {
        this.actionBarService = actionBarService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        actionBarService.updateNow(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        actionBarService.updateNow(event.getPlayer());
    }
}