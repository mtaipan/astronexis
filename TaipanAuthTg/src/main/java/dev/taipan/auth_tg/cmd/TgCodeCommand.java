package dev.taipan.auth_tg.cmd;

import dev.taipan.auth_tg.gate.GateListener;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class TgCodeCommand implements CommandExecutor {

    private final GateListener gate;

    public TgCodeCommand(GateListener gate) {
        this.gate = gate;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (args.length != 1) {
            p.sendMessage(ChatColor.YELLOW + "Использование: /tgcode <код>");
            return true;
        }

        boolean ok = gate.tryAcceptCode(p, args[0].trim());
        if (!ok) {
            p.sendMessage(ChatColor.RED + "Неверный/истёкший код.");
            return true;
        }

        p.sendMessage(ChatColor.GREEN + "Ок. Доступ открыт.");
        return true;
    }
}