package com.apve;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;

public class FoolProof {

    private final double high;
    private final double medium;
    private final boolean warns;
    private final boolean punishment;
    private final YamlConfiguration config;

    public static final String[] LATINIC_KEYS = {
            "insult-words", "family-insult-words", "family-roots",
            "bad-roots", "expressive-words", "ad-words",
            "allowed-words", "adult-words", "adult-roots"
    };

    public static final String[] LATINIC_SYMBOL_KEYS = {
            "social"
    };

    private static final String STRICT_LATIN_PATTERN = "^[a-z]+$";
    private static final String STRICT_LATIN_SYMBOL_PATTERN = "^[a-z0-9.-]+$";

    public FoolProof(YamlConfiguration config, List<String> errors, List<String> warnings) {
        this.config = config;
        this.high = config.getDouble("thresholds.high", 0.8); 
        this.medium = config.getDouble("thresholds.medium", 0.5);
        this.warns = booleanChecker(config, "warns.warns-is-enabled", false, errors);
        this.punishment = booleanChecker(config, "punishments.punishments-is-enabled", false, errors);
    }

    public static void validateDictionaries(YamlConfiguration config, List<String> errors, List<String> warnings) {
        for (String key : LATINIC_KEYS) {
            if (!config.contains(key)) continue;

            List<String> words = config.getStringList(key);
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (word == null || word.isBlank()) {
                    errors.add("'" + key + "' dictionary: Null or empty string found (Position " + (i + 1) + ").");
                    continue;
                }
                if (!word.matches(STRICT_LATIN_PATTERN)) {
                    errors.add(String.format("'%s' in '%s' has illegal symbols. Use lowercase latin characters only.", word, key));
                }
            }
        }

        for (String key : LATINIC_SYMBOL_KEYS) {
            if (!config.contains(key)) continue;

            List<String> words = config.getStringList(key);
            for (int i = 0; i < words.size(); i++) {
                String word = words.get(i);
                if (word == null || word.isBlank()) {
                    errors.add("'" + key + "' dictionary: Null or empty string found (Position " + (i + 1) + ").");
                    continue;
                }
                if (!word.matches(STRICT_LATIN_SYMBOL_PATTERN)) {
                    errors.add(String.format("'%s' in '%s' has illegal symbols. Use lowercase latin characters, numbers, dots, and hyphens only.", word, key));
                }
            }
        }
    }

    public ValidationResult validateAll() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateDictionaries(config, errors, warnings);

        Map<String, Integer> priorityMap = new HashMap<>();
        ConfigurationSection prioritySection = config.getConfigurationSection("priority");

        if (prioritySection == null) {
            errors.add("Section 'priority' not found.");
        } else {
            Set<Integer> uniquePriorities = new HashSet<>();
            String[] requiredPriorities = {"caps", "spam", "insult", "adult-content", "family-insult", "social-media", "advertisement"};
            
            for (String category : requiredPriorities) {
                if (!prioritySection.contains(category)) {
                    errors.add("Missing priority parameter for: 'priority." + category + "'");
                    continue;
                }
                if (!prioritySection.isInt(category)) {
                    errors.add("Parameter 'priority." + category + "' must be an integer.");
                    continue;
                }
                int value = prioritySection.getInt(category);
                if (!uniquePriorities.add(value)) {
                    errors.add("Duplicate priority detected. Priority '" + value + "' is assigned to multiple categories.");
                }
                priorityMap.put(category, value);
            }
        }

        if (high <= 0.0 || high > 1.0) errors.add("thresholds.high must be between 0.0 and 1.0!");
        if (medium <= 0.0 || medium > 1.0) errors.add("thresholds.medium must be between 0.0 and 1.0!");
        if (high <= medium) errors.add("thresholds.high cannot be lower than or equal to thresholds.medium!");

        if (this.warns && !this.punishment) {
            warnings.add("Warns is enabled but punishments isn't. Proper plugin operation is not guaranteed.");
        }

        for (String key : config.getKeys(false)) {
            if (config.isConfigurationSection(key)) {
                ConfigurationSection section = config.getConfigurationSection(key);
                if (section != null) {
                    if (section.contains("blocking")) booleanChecker(config, key + ".blocking", false, errors);
                    if (section.contains("censor")) booleanChecker(config, key + ".censor", false, errors);
                    if (section.contains("is-enabled")) booleanChecker(config, key + ".is-enabled", false, errors);

                    if (config.isBoolean(key + ".blocking") && config.isBoolean(key + ".censor")) {
                        if (config.getBoolean(key + ".blocking") && config.getBoolean(key + ".censor")) {
                            errors.add("'" + key + "' Category: Blocking mode is incompatible with censor mode.");
                        }
                    }
                }
            }
        }

        List<String> blockedDomains = config.getStringList("blocked-domains");
        if (blockedDomains.isEmpty()) {
            blockedDomains = Arrays.asList("ru", "com", "net", "org", "gg", "io", "xyz", "site", "online", "top", "me", "fun", "info", "shop", "store");
            warnings.add("List 'blocked-domains' is empty. Default list loaded.");
        }

        Set<String> interceptedCommands = new HashSet<>(config.getStringList("intercepted-commands"));
        if (interceptedCommands.isEmpty()) {
            interceptedCommands = Set.of("msg", "w", "tell", "m", "whisper", "pm", "r", "reply");
            warnings.add("List 'intercepted-commands' is empty. Default list loaded.");
        }

        return new ValidationResult(errors, warnings, priorityMap, blockedDomains, interceptedCommands);
    }

    private static boolean booleanChecker(YamlConfiguration config, String path, boolean defaultValue, List<String> errors) {
        if (!config.contains(path)) {
            errors.add("No parameter: " + path);
            return defaultValue;
        }
        if (!config.isBoolean(path)) {
            errors.add("'" + path + "' parameter can be only true or false. Found: '" + config.get(path) + "'");
            return defaultValue;
        }
        return config.getBoolean(path);
    }

    public record ValidationResult(
            List<String> errors, 
            List<String> warnings, 
            Map<String, Integer> priorityMap,
            List<String> blockedDomains,
            Set<String> interceptedCommands
    ) {
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }
}
