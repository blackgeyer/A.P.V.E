package com.apve;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class apve extends JavaPlugin implements Listener {

    private Logger suspiciousLogger;
    private PunishmentManager punishmentManager;

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().getSettings()
                .reEncodeByDefault(true)  
                .checkForUpdates(false);
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        checkConfigModification();

        setupSuspiciousLogger();

        this.punishmentManager = new PunishmentManager(this);
        getServer().getPluginManager().registerEvents(this, this);

        NetworkChatInterceptor.register(this, punishmentManager, suspiciousLogger);

        PacketEvents.getAPI().init();

        getLogger().info("Autonomous Potential Violation Eradicator plugin has been successfully activated!");
    }

    @Override
    public void onDisable() {
        PacketEvents.getAPI().terminate();
        getLogger().info("Autonomous Potential Violation Eradicator plugin deactivated.");
    }

    private void checkConfigModification() {
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) return;

        try (InputStream originalStream = getResource("config.yml")) {
            if (originalStream == null) return;

            String currentContent = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
            String originalContent = new String(originalStream.readAllBytes(), StandardCharsets.UTF_8);

            String currentHash = calculateHash(currentContent);
            String originalHash = calculateHash(originalContent);

            if (currentHash.equals(originalHash)) {
                getLogger().warning("==================================================");
                getLogger().warning("WARNING: You are using the not configured config.yml");
                getLogger().warning("This inresponsibility can lead to crashes or incorrect plugin work");
                getLogger().warning("Just changing some of the syntax of commands and some inappropiate words will not take much time, dude");
                getLogger().warning("Please, configure your config.yml before using the A.P.V.E!");
                getLogger().warning("==================================================");
            }
        } catch (Exception e) {
            getLogger().warning("Could not verify config.yml integrity: " + e.getMessage());
            getLogger().warning("Ensure you downloaded the A.P.V.E from official website (Modrinth like popular platforms) ");
            getLogger().warning("If you already installed the plugin from official website, contact with plugin's author.");
        }
    }

    private String calculateHash(String input) throws Exception {
        String normalizedInput = input.replace("\r\n", "\n");

        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] encodedhash = digest.digest(normalizedInput.getBytes(StandardCharsets.UTF_8));

        StringBuilder hexString = new StringBuilder();
        for (byte b : encodedhash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        return hexString.toString();
    }

    private void setupSuspiciousLogger() {
        suspiciousLogger = Logger.getLogger("SuspiciousChat");
        try {
            File logDir = new File(getDataFolder(), "logs");
            if (!logDir.exists()) logDir.mkdirs();
            FileHandler fh = new FileHandler(getDataFolder() + "/logs/suspicious.log", true);
            fh.setFormatter(new SimpleFormatter());
            suspiciousLogger.addHandler(fh);
            suspiciousLogger.setUseParentHandlers(false);
        } catch (IOException e) {
            getLogger().severe("Failed to create .log file: " + e.getMessage());
        }
    }

    public Logger getSuspiciousLogger() {
        return suspiciousLogger;
    }
}