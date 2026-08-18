package org.apve;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CommandManager implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_RELOAD = "apve.reload";
    private static final String PERMISSION_SHOW = "apve.warns.show";
    private static final String PERMISSION_REMOVE = "apve.warns.remove";
    private static final String PERMISSION_CLEAR = "apve.warns.clear";

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
            case "warns"  -> handleWarns(sender, args);
            case "help"   -> sendHelp(sender);
            default       -> handleUnknownCommand(sender);
        }

        return true;
    }

    // ─── HANDLERS ────────────────────────────────────────────────────────

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_RELOAD)) {
            sendFormatted(sender, "command-msg.perm-fail");
            return;
        }

        sendFormatted(sender, "command-msg.cfg-reload-msg");
        plugin.performReload(sender);
    }

    private void handleWarns(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sendHelp(sender);
            return;
        }

        String action = args[1].toLowerCase();
        String targetName = args[2];
        
        @SuppressWarnings("deprecation")
        OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
        UUID targetId = target.getUniqueId();

        switch (action) {
            case "show" -> {
                if (!sender.hasPermission(PERMISSION_SHOW)) {
                    sendFormatted(sender, "command-msg.perm-fail");
                    return;
                }
                
                int count = NetworkChatInterceptor.getWarns(targetId);
                String maxViolation = NetworkChatInterceptor.getHighestViolationType(targetId);
                
                sendFormatted(sender, "command-msg.warns.show", 
                        "{player}", targetName, 
                        "{amount}", String.valueOf(count), 
                        "{violation}", maxViolation);
            }
            case "remove" -> {
                if (!sender.hasPermission(PERMISSION_REMOVE)) {
                    sendFormatted(sender, "command-msg.perm-fail");
                    return;
                }
                if (args.length < 4) {
                    sendFormatted(sender, "command-msg.invalid-syntax", "{usage}", "/apve warns remove <nickname> <amount>");
                    return;
                }
                try {
                    int amountToRemove = Integer.parseInt(args[3]);
                    int currentWarns = NetworkChatInterceptor.getWarns(targetId);
                    
                    if (currentWarns == 0) {
                        sendFormatted(sender, "command-msg.warns.no-warns", "{player}", targetName);
                        return;
                    }
                    
                    int leftover = NetworkChatInterceptor.removeWarns(targetId, amountToRemove);
                    
                    sendFormatted(sender, "command-msg.warns.removed", 
                            "{player}", targetName, 
                            "{amount}", String.valueOf(amountToRemove), 
                            "{left}", String.valueOf(leftover));
                            
                } catch (NumberFormatException e) {
                    sendFormatted(sender, "command-msg.invalid-number", "{arg}", args[3]);
                }
            }
            case "clear" -> {
                if (!sender.hasPermission(PERMISSION_CLEAR)) {
                    sendFormatted(sender, "command-msg.perm-fail");
                    return;
                }
                NetworkChatInterceptor.clearWarns(targetId);
                sendFormatted(sender, "command-msg.warns.cleared", "{player}", targetName);
            }
            default -> sendHelp(sender);
        }
    }

    private void handleUnknownCommand(CommandSender sender) {
        sendFormatted(sender, "command-msg.help-command-view-req");
    }

    private void sendHelp(CommandSender sender) {
        List<String> helpList = plugin.getConfig().getStringList("command-msg.help-command-msg");
        if (helpList.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Help section is missing in config.yml!");
            return;
        }
        for (String line : helpList) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
        }
    }

    // ─── TAB COMPLETE ─────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission(PERMISSION_RELOAD)) completions.add("reload");
            if (sender.hasPermission(PERMISSION_SHOW) || 
                sender.hasPermission(PERMISSION_REMOVE) || 
                sender.hasPermission(PERMISSION_CLEAR)) {
                completions.add("warns");
            }
            completions.add("help");
            return filterPrefix(completions, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("warns")) {
            if (sender.hasPermission(PERMISSION_SHOW)) completions.add("show");
            if (sender.hasPermission(PERMISSION_REMOVE)) completions.add("remove");
            if (sender.hasPermission(PERMISSION_CLEAR)) completions.add("clear");
            return filterPrefix(completions, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("warns")) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                completions.add(player.getName());
            }
            return filterPrefix(completions, args[2]);
        }

        return List.of();
    }

    // ─── UTILS (DYNAMIC FORMATTING) ───────────────────────────────────────


    private void sendFormatted(CommandSender sender, String path, String... replacements) {
        String msg = plugin.getConfig().getString(path, "&cMissing config string: " + path);
        
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                msg = msg.replace(replacements[i], replacements[i + 1]);
            }
        }
        
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }

    private List<String> filterPrefix(List<String> list, String prefix) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .toList();
    }
}