package com.apve;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_RELOAD = "apve.reload";

    private final apve plugin;

    public CommandManager(apve plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload" -> handleReload(sender);
            default       -> sendHelp(sender);
        }

        return true;
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sender.sendMessage(plugin.getConfig().getString("command-msg.perm-fail"));
            return;
        }

        sender.sendMessage(plugin.getConfig().getString("command-msg.cfg-reload-msg"));
        plugin.performReload(sender);
    }

    // ─── TAB COMPLETE ─────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("reload");
        return List.of();
    }

    // ─── HELP ─────────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.getConfig().getString("command-msg.av-commands"));
        sender.sendMessage(plugin.getConfig().getString("command-msg.command-one"));
    }
}