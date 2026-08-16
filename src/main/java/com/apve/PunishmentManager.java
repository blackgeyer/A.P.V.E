package com.apve;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class PunishmentManager {

    private final JavaPlugin plugin;
    private final List<String> permanentKeywords;

    public PunishmentManager(JavaPlugin plugin) {
        this.plugin = plugin;
        
        List<String> configList = plugin.getConfig().getStringList("punishments.perm-keywords");
        
        if (configList.isEmpty()) {
            this.permanentKeywords = Arrays.asList("permanent", "perm", "infinity", "inf", "-1");
        } else {
            this.permanentKeywords = configList.stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
        }
    }

    public void mutePlayer(UUID playerUUID, String reason, String durationStr) {
        String commandTemplate = plugin.getConfig().getString("punishments.mute-command", "mute %player% %duration% %reason%");
        executeCommand(commandTemplate, playerUUID, null, reason, durationStr);
    }

    public void banPlayer(UUID playerUUID, String reason, String durationStr) {
        String commandTemplate = plugin.getConfig().getString("punishments.ban-command", "ban %player% %duration% %reason%");
        executeCommand(commandTemplate, playerUUID, null, reason, durationStr);
    }

    public void banipPlayer(String ipAddress, String reason, String durationStr) {
        String commandTemplate = plugin.getConfig().getString("punishments.banip-command", "banip %ip% %duration% %reason%");
        executeCommand(commandTemplate, null, ipAddress, reason, durationStr);
    }

    public void kickPlayer(UUID playerUUID, String reason) {
        String commandTemplate = plugin.getConfig().getString("punishments.kick-command", "kick %player% %reason%");
        executeCommand(commandTemplate, playerUUID, null, reason, "");
    }

    private void executeCommand(String template, UUID playerUUID, String ipAddress, String reason, String durationStr) {
        boolean punishmentsEnabled = plugin.getConfig().getBoolean("punishments.punishments-is-enabled", true);
        if (!punishmentsEnabled) {
            return;
        }

        if (template == null || template.trim().isEmpty()) {
            return;
        }

        if (durationStr != null && permanentKeywords.contains(durationStr.toLowerCase().trim())) {
            durationStr = "perm"; 

        }

        String command = template;

        if (playerUUID != null) {
            Player onlineTarget = Bukkit.getPlayer(playerUUID);
            String playerName;

            if (onlineTarget != null) {
                playerName = onlineTarget.getName();
            } else {
                OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(playerUUID);
                playerName = offlineTarget.getName() != null ? offlineTarget.getName() : playerUUID.toString();
            }

            command = command.replace("%player%", playerName);
        }

        if (ipAddress != null) {
            command = command.replace("%ip%", ipAddress);
        }

        String formattedReason = reason != null ? reason : "Rule-violating";
        formattedReason = ChatColor.translateAlternateColorCodes('&', formattedReason);
        command = command.replace("%reason%", formattedReason);

        String formattedDuration = durationStr != null ? durationStr.trim() : "";
        command = command.replace("%duration%", formattedDuration);

        final String finalCommand = command.trim().replaceAll("\\s+", " ");

        if (finalCommand.isEmpty()) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
        });
    }
}