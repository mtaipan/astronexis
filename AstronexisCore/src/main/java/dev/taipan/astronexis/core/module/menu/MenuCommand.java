package dev.taipan.astronexis.core.module.menu;

import dev.taipan.astronexis.core.shared.i18n.MessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class MenuCommand implements CommandExecutor {

    private final MenuService menuService;
    private final MessageService messages;

    public MenuCommand(MenuService menuService, MessageService messages) {
        this.menuService = menuService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get("ru", "message.player-only"));
            return true;
        }

        if (!player.hasPermission("astronexis.menu")) {
            player.sendMessage(messages.get(player, "message.no-permission"));
            return true;
        }

        menuService.open(player, MenuPage.MAIN);
        return true;
    }
}