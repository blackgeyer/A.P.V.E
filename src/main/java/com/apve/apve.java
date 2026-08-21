package org.apve;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
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

        YamlConfiguration yamlConfig = (YamlConfiguration) getConfig();
        List<String> initErrors = new ArrayList<>();
        List<String> initWarnings = new ArrayList<>();

        FoolProof foolProof = new FoolProof(yamlConfig, initErrors, initWarnings);
        FoolProof.ValidationResult result = foolProof.validateAll();

        List<String> totalWarnings = new ArrayList<>(initWarnings);
        totalWarnings.addAll(result.warnings());

        List<String> totalErrors = new ArrayList<>(initErrors);
        totalErrors.addAll(result.errors());

        if (!totalWarnings.isEmpty()) {
            getLogger().warning("Configuration Warnings Found:");
            for (String warn : totalWarnings) {
                getLogger().warning(" [!] " + warn);
            }
            getLogger().warning("Proper plugin operation is not guaranteed.");
        }

        if (!totalErrors.isEmpty()) {
            getLogger().severe("CRITICAL CONFIGURATION ERRORS!");
            for (String err : totalErrors) {
                getLogger().severe(" [X] " + err);
            }
            getLogger().severe("Plugin disabled due to critical errors.");

            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        setupSuspiciousLogger();

        this.punishmentManager = new PunishmentManager(this);
        getServer().getPluginManager().registerEvents(this, this);

        NetworkChatInterceptor.register(this, punishmentManager, suspiciousLogger, result);

        PacketEvents.getAPI().init();

        getCommand("apve").setExecutor(new CommandManager(this));
        getCommand("apve").setTabCompleter(new CommandManager(this));

        getLogger().info("Autonomous Potential Violation Eradicator [A.P.V.E] plugin has been successfully activated!");
    }

    @Override
    public void onDisable() {
        if (PacketEvents.getAPI() != null && PacketEvents.getAPI().isInitialized()) {
            PacketEvents.getAPI().terminate();
        }
        getLogger().info("Autonomous Potential Violation Eradicator plugin deactivated.");
    }

    // ─── RELOAD ──────────────────────────────────────────────────────────

    public void performReload(CommandSender sender) {
        reloadConfig();
        checkConfigModification();

        YamlConfiguration yamlConfig = (YamlConfiguration) getConfig();
        List<String> initErrors = new ArrayList<>();
        List<String> initWarnings = new ArrayList<>();

        FoolProof foolProof = new FoolProof(yamlConfig, initErrors, initWarnings);
        FoolProof.ValidationResult result = foolProof.validateAll();

        List<String> totalWarnings = new ArrayList<>(initWarnings);
        totalWarnings.addAll(result.warnings());

        List<String> totalErrors = new ArrayList<>(initErrors);
        totalErrors.addAll(result.errors());

        if (!totalWarnings.isEmpty()) {
            getLogger().warning("Configuration Warnings Found:");
            for (String warn : totalWarnings) {
                getLogger().warning(" [!] " + warn);
            }
            getLogger().warning("Proper plugin operation is not guaranteed.");
        }

        if (!totalErrors.isEmpty()) {
            getLogger().severe("CRITICAL CONFIGURATION ERRORS!");
            for (String err : totalErrors) {
                getLogger().severe(" [X] " + err);
            }
            getLogger().severe("Reload aborted due to critical errors.");

            sender.sendMessage("[APVE] Reload failed. Check console for errors.");
            return;
        }

        NetworkChatInterceptor.loadConfig(getConfig(), result);

        sender.sendMessage("[APVE] Config reloaded successfully.");
        getLogger().info("Config reloaded successfully.");
    }

    // ─── INTERNALS ───────────────────────────────────────────────────────

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
                getLogger().warning("WARNING: You are using the default unconfigured config.yml!");
                getLogger().warning("Please configure your config.yml before using A.P.V.E.");
                getLogger().warning("Without configuring the config.yml, proper plugin operation not guaranteed.");
            }
        } catch (Exception e) {
            getLogger().warning("Could not verify config.yml integrity: " + e.getMessage());
            getLogger().warning("Proper plugin opeartion is not guaranteed.");
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