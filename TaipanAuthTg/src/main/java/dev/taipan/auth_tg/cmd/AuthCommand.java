package dev.taipan.auth_tg.cmd;

import dev.taipan.auth_tg.bedrock.BedrockUiManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AuthCommand implements CommandExecutor {

    private final BedrockUiManager ui;

    public AuthCommand(BedrockUiManager ui) {
        this.ui = ui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        ui.openFor(p);
        return true;
    }
}