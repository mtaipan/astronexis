package dev.taipan.auth_tg.cmd.admin;

import dev.taipan.auth_tg.config.PluginConfig;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.plugin.java.JavaPlugin;

public final class AuthTgCommand implements CommandExecutor {

    private final JavaPlugin plugin;
    private final PluginConfig cfg;

    public AuthTgCommand(JavaPlugin plugin, PluginConfig cfg) {
        this.plugin = plugin;
        this.cfg = cfg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("taipan.authtg.admin")) {
            sender.sendMessage(ChatColor.RED + "Нет прав.");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "authtg: storage=" + cfg.storageType());
        sender.sendMessage(ChatColor.GRAY + "Дальше сюда добавим админку (каналы/обяз. подписки/бан tgId).");
        return true;
    }
}