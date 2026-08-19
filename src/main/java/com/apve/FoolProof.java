package org.apve;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.*;
import java.util.regex.Pattern;

public class FoolProof {

    private final double high;
    private final double medium;
    private final boolean warns;
    private final boolean punishment;
    private final YamlConfiguration config;

    public static final String[] LATINIC_KEYS = {
            "insult-words", "family-insult-words", "family-roots",
            "bad-roots", "expressive-words", "ad-words",
            "allowed-words", "adult-words", "adult-roots", "social"
    };

    private static final String STRICT_LATIN_PATTERN = "^[a-z]+$";
    
    private static final Pattern DURATION_PATTERN = Pattern.compile("^(\\d+(s|m|h|d|w|mo|y))+$", Pattern.CASE_INSENSITIVE);

    public static final Set<String> PUNISHMENT_TYPE = Set.of(
            "mute", "ban", "banip", "none", "warn", "kick"
    );

    public FoolProof(YamlConfiguration config, List<String> errors, List<String> warnings) {
        this.config = config;
        this.high = config.getDouble("thresholds.high", 0.8); 
        this.medium = config.getDouble("thresholds.medium", 0.5);
        this.warns = booleanChecker(config, "warns.warns-is-enabled", false, errors);
        this.punishment = booleanChecker(config, "punishments.punishments-is-enabled", false, errors);
    }

    public ValidationResult validateAll() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        List<String> rawDomains = config.getStringList("blocked-domains");
        Set<String> blockedDomains = new HashSet<>();

        List<String> rawCommands = config.getStringList("intercepted-commands");
        Set<String> interceptedCommands = new HashSet<>();

        List<String> rawPermKeywords = config.getStringList("punishments.perm-keywords");
        Set<String> permKeywords = new HashSet<>();

        Map<String, Set<String>> cachedDictionaries = new HashMap<>();

        for (String key : LATINIC_KEYS) {
            if (!config.contains(key)) continue;

            List<String> rawWords = config.getStringList(key);
            Set<String> wordSet = new HashSet<>(rawWords.size());

            for (int i = 0; i < rawWords.size(); i++) {
                String word = rawWords.get(i);
                if (word == null || word.isBlank()) {
                    errors.add("'" + key + "' dictionary: Null or empty string found (Position " + (i + 1) + ").");
                    continue;
                }
                String lowerWord = word.toLowerCase(Locale.ROOT);
                if (!lowerWord.matches(STRICT_LATIN_PATTERN)) {
                    errors.add(String.format("'%s' in '%s' has illegal symbols. Use lowercase latin characters only.", word, key));
                } else {
                    wordSet.add(lowerWord);
                }
            }
            cachedDictionaries.put(key, wordSet);
        }

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

                    if (section.contains("type")) {
                        typeChecker(config, key + ".type", "none", errors, warnings);
                    }
                }
            }
        }

        if (rawPermKeywords.isEmpty()) {
            warnings.add("List 'punishments.perm-keywords' is empty. Proper plugin operation is not guaranteed.");
        } else {
            for (String permKeyW : rawPermKeywords) {
                permKeywords.add(permKeyW.toLowerCase(Locale.ROOT));
            }
        }

        validateReasons(errors);

        validateDurations(errors, permKeywords);

        if (rawDomains.isEmpty()) {
            warnings.add("List 'blocked-domains' is empty. Proper plugin operation is not guaranteed.");
        } else {
            for (String domain : rawDomains) {
                blockedDomains.add(domain.toLowerCase(Locale.ROOT));
            }
        }

        if (rawCommands.isEmpty()) {
            warnings.add("List 'intercepted-commands' is empty. Proper plugin operation is not guaranteed.");
        } else {
            for (String cmd : rawCommands) {
                interceptedCommands.add(cmd.toLowerCase(Locale.ROOT));
            }
        }

        return new ValidationResult(errors, warnings, priorityMap, cachedDictionaries, blockedDomains, interceptedCommands);
    }

    private void validateDurations(List<String> errors, Set<String> permKeywords) {
        for (String key : config.getKeys(true)) {
            if (isDurationKey(key)) {
                if (!config.isString(key)) {
                    errors.add("Parameter '" + key + "' must be a String duration (e.g. '30m', '2h').");
                    continue;
                }

                String rawValue = config.getString(key);
                if (rawValue == null || rawValue.isBlank()) {
                    errors.add("Duration parameter '" + key + "' cannot be null or empty.");
                    continue;
                }

                String cleanValue = rawValue.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
                String lowerValue = cleanValue.toLowerCase(Locale.ROOT);

                if (permKeywords.contains(lowerValue)) {
                    continue;
                }

                if (!DURATION_PATTERN.matcher(cleanValue).matches()) {
                    errors.add("Invalid duration format in '" + key + "': '" + rawValue + "'.");
                }
            }
        }
    }

    private boolean isDurationKey(String key) {
        return key.endsWith(".duration") || key.equals("warns.warn-reset-time");
    }

    private void validateReasons(List<String> errors) {
        for (String key : config.getKeys(true)) {
            if (isReasonKey(key)) {
                if (config.isList(key)) {
                    List<String> lines = config.getStringList(key);
                    if (lines.isEmpty()) {
                        errors.add("Parameter '" + key + "' is an empty list.");
                    } else {
                        for (int i = 0; i < lines.size(); i++) {
                            checkStringStrict(lines.get(i), key + " (Line " + (i + 1) + ")", errors);
                        }
                    }
                } else {
                    String rawValue = config.getString(key);
                    checkStringStrict(rawValue, key, errors);
                }
            }
        }
    }

    private boolean isReasonKey(String key) {
        return key.endsWith(".reason") 
            || key.endsWith(".blocking-reason") 
            || key.endsWith(".censor-reason");
    }

    private void checkStringStrict(String value, String path, List<String> errors) {
        if (value == null) {
            errors.add("Missing required text parameter: '" + path + "'");
            return;
        }

        String cleaned = value.trim().replaceAll("^[\"']+|[\"']+$", "").trim();

        if (cleaned.isEmpty()) {
            errors.add("Parameter '" + path + "' cannot be empty or contain only quotes/whitespaces.");
        }
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

    private static String typeChecker(YamlConfiguration config, String path, String defaultValue, List<String> errors, List<String> warnings) {
        if (!config.contains(path)) {
            errors.add("Missing required parameter: '" + path + "'");
            return defaultValue;
        }
        if (!config.isString(path)) {
            errors.add("Parameter '" + path + "' must be a string.");
            return defaultValue;
        }
        String rawValue = config.getString(path);
        if (rawValue == null || rawValue.isBlank()) {
            errors.add("Parameter '" + path + "' cannot be null or empty.");
            return defaultValue;
        }

        String cleanValue = rawValue.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
        String normalizedValue = cleanValue.toLowerCase(Locale.ROOT);

        if (!PUNISHMENT_TYPE.contains(normalizedValue)) {
            errors.add("Invalid punishment type in '" + path + "': '" + rawValue + "'. Allowed types are '" + PUNISHMENT_TYPE + "'.");
            return defaultValue;
        }

        if ("none".equalsIgnoreCase(rawValue)) {
            warnings.add("Punishment type '" + rawValue + "' in '" + path + "' equals none. Proper plugin operation is not guaranteed.");
        }
        return normalizedValue;
    }

    public record ValidationResult(
            List<String> errors, 
            List<String> warnings, 
            Map<String, Integer> priorityMap,
            Map<String, Set<String>> dictionaries,
            Set<String> blockedDomains,
            Set<String> interceptedCommands
    ) {
        public boolean hasErrors() { return !errors.isEmpty(); }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
        public boolean containsWord(String dictionaryKey, String word) {
            Set<String> set = dictionaries.get(dictionaryKey);
            return set != null && set.contains(word.toLowerCase(Locale.ROOT));
        }
    }
}