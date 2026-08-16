package com.apve;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatMessage;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class NetworkChatInterceptor {

    // ─── VIOLATION TYPES ─────────────────────────────────────────────────
    public enum ViolationType {
        INSULT,
        FAMILY_INSULT,
        ADVERTISEMENT,
        SOCIAL_MEDIA,
        ADULT_CONTENT,
        SPAM,
        CAPS;

        private int priority;

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }
    }

    // ─── STORED VIOLATION RECORD ─────────────────────────────────────────
    private record StoredViolation(
        ViolationType type,
        ViolationRule rule,
        String reasonDetail,
        String badWord
    ) {}

    // ─── AHO-CORASICK IMPLEMENTATION ─────────────────────────────────────
    public static class AhoCorasick {
        public record Match(String pattern, ViolationType type) {}

        private static class Node {
            final Map<Character, Node> children = new HashMap<>();
            Node fail;
            final List<Match> outputs = new ArrayList<>();
        }

        private final Node root = new Node();

        public void addPattern(String pattern, ViolationType type) {
            if (pattern == null || pattern.isEmpty()) return;
            Node current = root;
            for (char ch : pattern.toLowerCase().toCharArray()) {
                current = current.children.computeIfAbsent(ch, k -> new Node());
            }
            current.outputs.add(new Match(pattern, type));
        }

        public void build() {
            Queue<Node> queue = new LinkedList<>();
            for (Node child : root.children.values()) {
                child.fail = root;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                Node current = queue.poll();
                for (Map.Entry<Character, Node> entry : current.children.entrySet()) {
                    char ch = entry.getKey();
                    Node child = entry.getValue();
                    Node fallback = current.fail;
                    while (fallback != null && !fallback.children.containsKey(ch)) {
                        fallback = fallback.fail;
                    }
                    child.fail = (fallback != null) ? fallback.children.get(ch) : root;
                    child.outputs.addAll(child.fail.outputs);
                    queue.add(child);
                }
            }
        }

        public List<Match> search(String text) {
            List<Match> results = new ArrayList<>();
            Node current = root;
            String lowerText = text.toLowerCase();
            for (int i = 0; i < lowerText.length(); i++) {
                char ch = lowerText.charAt(i);
                while (current != null && !current.children.containsKey(ch)) {
                    current = current.fail;
                }
                current = (current != null) ? current.children.get(ch) : root;
                results.addAll(current.outputs);
            }
            return results;
        }
    }

    // ─── SPAM HISTORY ENTRY ──────────────────────────────────────────────
    private static class SpamEntry {
        final String normalizedText;
        final long   timestamp;
        SpamEntry(String normalizedText, long timestamp) {
            this.normalizedText = normalizedText;
            this.timestamp      = timestamp;
        }
    }

    // ─── DYNAMIC CONFIG MODELS ───────────────────────────────────────────
    private record ViolationRule(
        boolean enabled, boolean punishEnabled, String type, String duration, String reason,
        boolean block, String blockReason, boolean censor, String censorReason
    ) {}

    private record GlobalConfig(
        boolean warnsIsEnabled, boolean warnLimitIsEnabled, int warnLimit, String warnMessage, String lastWarnMessage,
        boolean tempWarns, String warnResetTime, int warnResetCount,
        Map<ViolationType, ViolationRule> rules
    ) {}

    private record ChatRulesCache(
        double highThreshold, double mediumThreshold, Set<String> allowedWords,
        List<String> insultWords, Set<String> familyWords, Set<String> expressiveWords, List<String> adultWords,
        List<String> socialWords, Pattern domainPattern, Set<String> interceptedCommands,
        boolean spamModuleEnabled, int spamMaxCount, long spamWindowMs, double spamSimThreshold,
        boolean capsModuleEnabled, int capsMinLength, int capsMinPct,
        AhoCorasick ahoCorasick
    ) {}

    private static volatile GlobalConfig   cachedConfig;
    private static volatile ChatRulesCache cachedRules;

    // ─── STATIC STATE ────────────────────────────────────────────────────
    private static final Map<UUID, Integer>          warnCounts        = new ConcurrentHashMap<>();
    private static final Map<UUID, StoredViolation>  highestViolations = new ConcurrentHashMap<>();
    private static final Map<UUID, Deque<SpamEntry>> spamHistory       = new ConcurrentHashMap<>();
    private static final Map<UUID, BukkitTask>       resetTasks        = new ConcurrentHashMap<>();
    private static final Set<UUID> externallyMutedPlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<UUID>          pendingBlockMessages  = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Map<UUID, String>  pendingCensorMessages = new ConcurrentHashMap<>();
    private static final Set<UUID>          apveCancelledMessages = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // ─── CONSTANTS ───────────────────────────────────────────────────────
    private static final Set<String> PERSONAL_PRONOUNS = Set.of(
            "ty", "vy", "on", "ona", "oni", "tebe", "tebya", "toboy", "vas", "vam", "emu", "ey",
            "tvoya", "tvoyu", "tvoy", "tvoego", "tvoemu", "tvoim", "vashu", "vashe", "vash",
            "ego", "eyo", "ih", "you", "your", "he", "she", "they", "his", "her", "their", "u");

    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)[\\._,\\s\\-]){3}" +
            "(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\b");

    private static final Pattern NON_LETTER_PATTERN = Pattern.compile("[^a-zA-Z\u0400-\u04FF]");
    private static final Pattern COMPRESS_PATTERN   = Pattern.compile("[\\s._\\-,]+");
    private static final Pattern NEWLINE_PATTERN    = Pattern.compile("\n");

    // ─── LOAD CONFIG ─────────────────────────────────────────────────────
    public static void loadConfig(FileConfiguration config) {

        ViolationType.FAMILY_INSULT.setPriority(config.getInt("priority.family-insult", 5));
        ViolationType.ADVERTISEMENT.setPriority(config.getInt("priority.advertisement", 7));
        ViolationType.SOCIAL_MEDIA.setPriority(config.getInt("priority.social-media", 6));
        ViolationType.ADULT_CONTENT.setPriority(config.getInt("priority.adult-content", 4));
        ViolationType.INSULT.setPriority(config.getInt("priority.insult", 3));
        ViolationType.SPAM.setPriority(config.getInt("priority.spam", 2));
        ViolationType.CAPS.setPriority(config.getInt("priority.caps", 1));
        
        Map<ViolationType, ViolationRule> rulesMap = new EnumMap<>(ViolationType.class);

        rulesMap.put(ViolationType.INSULT, new ViolationRule(
                config.getBoolean("insult.is-enabled", true),
                config.getBoolean("insult.punishment-is-enabled", true),
                config.getString("insult.type", "mute").toLowerCase(),
                config.getString("insult.duration", "30m"),
                config.getString("insult.reason", "&cRule violated"),
                config.getBoolean("insult.blocking", false),
                config.getString("insult.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("insult.censor", false),
                config.getString("insult.censor-reason", "&cMessage censored")
        ));

        rulesMap.put(ViolationType.FAMILY_INSULT, new ViolationRule(
                config.getBoolean("family-insult.is-enabled", true),
                config.getBoolean("family-insult.punishment-is-enabled", true),
                config.getString("family-insult.type", "mute").toLowerCase(),
                config.getString("family-insult.duration", "30m"),
                config.getString("family-insult.reason", "&cRule violated"),
                config.getBoolean("family-insult.blocking", false),
                config.getString("family-insult.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("family-insult.censor", false),
                config.getString("family-insult.censor-reason", "&cMessage censored")
        ));

        rulesMap.put(ViolationType.ADVERTISEMENT, new ViolationRule(
                config.getBoolean("ad-dist.is-enabled", true),
                config.getBoolean("ad-dist.punishment-is-enabled", true),
                config.getString("ad-dist.type", "mute").toLowerCase(),
                config.getString("ad-dist.duration", "30m"),
                config.getString("ad-dist.reason", "&cRule violated"),
                config.getBoolean("ad-dist.blocking", false),
                config.getString("ad-dist.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("ad-dist.censor", false),
                config.getString("ad-dist.censor-reason", "&cMessage censored")
        ));

        rulesMap.put(ViolationType.SOCIAL_MEDIA, new ViolationRule(
                config.getBoolean("soc-media-dist.is-enabled", true),
                config.getBoolean("soc-media-dist.punishment-is-enabled", true),
                config.getString("soc-media-dist.type", "mute").toLowerCase(),
                config.getString("soc-media-dist.duration", "30m"),
                config.getString("soc-media-dist.reason", "&cRule violated"),
                config.getBoolean("soc-media-dist.blocking", false),
                config.getString("soc-media-dist.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("soc-media-dist.censor", false),
                config.getString("soc-media-dist.censor-reason", "&cMessage censored")
        ));

        rulesMap.put(ViolationType.ADULT_CONTENT, new ViolationRule(
                config.getBoolean("adult-content.is-enabled", true),
                config.getBoolean("adult-content.punishment-is-enabled", true),
                config.getString("adult-content.type", "mute").toLowerCase(),
                config.getString("adult-content.duration", "30m"),
                config.getString("adult-content.reason", "&cRule violated"),
                config.getBoolean("adult-content.blocking", false),
                config.getString("adult-content.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("adult-content.censor", false),
                config.getString("adult-content.censor-reason", "&cMessage censored")
        ));

        rulesMap.put(ViolationType.SPAM, new ViolationRule(
                config.getBoolean("spam.is-enabled", true),
                config.getBoolean("spam.punishment-is-enabled", true), 
                config.getString("spam.type", "mute").toLowerCase(),
                config.getString("spam.duration", "30m"),
                config.getString("spam.reason", "&cRule violated"),
                config.getBoolean("spam.blocking", false),
                config.getString("spam.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("spam.censor", false),
                config.getString("spam.censor-reason", "&cMessage censored")
        ));

        rulesMap.put(ViolationType.CAPS, new ViolationRule(
                config.getBoolean("caps.is-enabled", true),
                config.getBoolean("caps.punishment-is-enabled", true),
                config.getString("caps.type", "mute").toLowerCase(),
                config.getString("caps.duration", "30m"),
                config.getString("caps.reason", "&cRule violated"),
                config.getBoolean("caps.blocking", false),
                config.getString("caps.blocking-reason", "&cYour message was blocked."),
                config.getBoolean("caps.censor", false),
                config.getString("caps.censor-reason", "&cMessage censored")
        ));

        cachedConfig = new GlobalConfig(
            config.getBoolean("warns.warns-is-enabled", true), 
            config.getBoolean("warns.warn-limit-is-enabled", true),
            config.getInt("warns.warn-limit", 2),
            config.getString("warns.warn-message", "&cYour message has been censored due to rule-violating words/meanings in it\n&cPlease, communicate in the chat without any rules-violating words/meanings."),
            config.getString("warns.last-warn-message", "&cThis is last warn. Don't write the rule-violating message.\n&cOtherwise, you will be punished by A.P.V.E."),
            config.getBoolean("warns.temporary-warns", true),
            config.getString("warns.warn-reset-time", "1h"),
            config.getInt("warns.warn_reset_count", 2), 
            rulesMap
        );

        AhoCorasick ac = new AhoCorasick();
        
        for (String root : config.getStringList("bad-roots")) 
            ac.addPattern(root, ViolationType.INSULT);
        for (String root : config.getStringList("adult-roots")) 
            ac.addPattern(root, ViolationType.ADULT_CONTENT);
        for (String word : config.getStringList("ad-words")) 
            ac.addPattern(word, ViolationType.ADVERTISEMENT);
        
        List<String> socialWords = config.getStringList("social");
        for (String word : socialWords) 
            ac.addPattern(word, ViolationType.SOCIAL_MEDIA);
        
        ac.build();

        Set<String> familyContextWords = new HashSet<>(config.getStringList("family-insult-words"));
        familyContextWords.addAll(config.getStringList("family-roots"));

        List<String> blockedDomains = config.getStringList("blocked-domains");
        if (blockedDomains.isEmpty()) {
            blockedDomains = Arrays.asList("ru", "com", "net", "org", "gg", "io", "xyz", "site", "online", "top", "me", "fun", "info", "shop", "store");
        }
        String domainRegex = "(?i)\\b[a-z0-9\\-_]+\\.(?:" + String.join("|", blockedDomains) + ")\\b";
        Pattern domainPattern = Pattern.compile(domainRegex);

        Set<String> interceptedCommands = new HashSet<>(config.getStringList("intercepted-commands"));
        if (interceptedCommands.isEmpty()) {
            interceptedCommands = Set.of("msg", "w", "tell", "m", "whisper", "pm", "r", "reply");
        }

        cachedRules = new ChatRulesCache(
            config.getDouble("thresholds.high", 0.75),
            config.getDouble("thresholds.medium", 0.50),
            new HashSet<>(config.getStringList("allowed-words")),
            config.getStringList("insult-words"), 
            familyContextWords,
            new HashSet<>(config.getStringList("expressive-words")),
            config.getStringList("adult-words"),
            socialWords,
            domainPattern,
            interceptedCommands,
            config.getBoolean("spam.is-enabled", true),
            config.getInt("spam.max-similar-messages", 3),
            config.getLong("spam.time-window-seconds", 60) * 1000L,
            config.getDouble("spam.similarity-threshold", 0.75),
            config.getBoolean("caps.is-enabled", true),
            config.getInt("caps.min-message-length", 6),
            config.getInt("caps.min-caps-percentage", 70),
            ac
        );
    }

    // ─── REGISTER ────────────────────────────────────────────────────────
    public static void register(JavaPlugin plugin, PunishmentManager punishmentManager, Logger suspiciousLogger) {

        loadConfig(plugin.getConfig());

        PacketEvents.getAPI().getEventManager().registerListener(
            new PacketListenerAbstract(PacketListenerPriority.HIGH) {
                @Override
                public void onPacketReceive(PacketReceiveEvent event) {

                    boolean isChatMsg = event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE;
                    boolean isChatCmd = event.getPacketType() == PacketType.Play.Client.CHAT_COMMAND;

                    if (!isChatMsg && !isChatCmd) return;

                    Player player = Bukkit.getPlayer(event.getUser().getUUID());
                    if (player == null) return;

                    final ChatRulesCache rules = cachedRules;
                    final GlobalConfig   cfg   = cachedConfig;

                    String rawText = extractRawText(event, isChatMsg, isChatCmd, rules.interceptedCommands());
                    if (rawText == null || rawText.isEmpty()) return;

                    String normalized = TextNormalizer.normalize(rawText);

                    // ── Spam / Caps — 
                    ViolationType spamCandidate = null;
                    if (rules.spamModuleEnabled() && checkAndRecordSpam(
                            player.getUniqueId(), normalized,
                            rules.spamMaxCount(), rules.spamWindowMs(), rules.spamSimThreshold())) {
                        spamCandidate = ViolationType.SPAM;
                    }

                    ViolationType capsCandidate = null;
                    if (rules.capsModuleEnabled() && isCaps(rawText, rules.capsMinLength(), rules.capsMinPct())) {
                        capsCandidate = ViolationType.CAPS;
                    }

                    // ── Link / IP / Domain ───────────────────────────────
                    String  compressed    = COMPRESS_PATTERN.matcher(rawText.toLowerCase()).replaceAll("");
                    boolean hasLinkBypass = compressed.contains("http") || compressed.contains("www");

                    if (containsIP(rawText) || rules.domainPattern().matcher(rawText).find() || hasLinkBypass) {
                        ViolationType detected = ViolationType.ADVERTISEMENT;
                        String reason = "IP/Link/Domain";

                        for (String social : rules.socialWords()) {
                            if (rawText.toLowerCase().contains(social.toLowerCase())) {
                                detected = ViolationType.SOCIAL_MEDIA;
                                reason = "Social Media Link: " + social;
                                break;
                            }
                        }

                        dispatch(plugin, player, punishmentManager, event, rawText, rawText, detected, reason, cfg, isChatMsg);
                        return;
                    }

                    String[] normWords = normalized.split("\\s+");
                    String[] rawWords  = rawText.toLowerCase().split("\\s+");

                    List<AhoCorasick.Match> acMatches = rules.ahoCorasick().search(normalized);
                    if (!acMatches.isEmpty()) {
                        AhoCorasick.Match match = acMatches.get(0);
                        ViolationType finalType = match.type();

                        if (finalType == ViolationType.INSULT) {
                            int insultIdx = -1;
                            for (int j = 0; j < normWords.length; j++) {
                                if (normWords[j].contains(match.pattern())) {
                                    insultIdx = j;
                                    break;
                                }
                            }
                            if (hasFamilyContext(normWords, rules.familyWords(), insultIdx)) {
                                finalType = ViolationType.FAMILY_INSULT;
                            }
                        }

                        dispatch(plugin, player, punishmentManager, event, rawText, match.pattern(), finalType, "Found via AC: " + match.pattern(), cfg, isChatMsg);
                        return;
                    }

                    ViolationType detectedType  = null;
                    String        matchedWord   = "";
                    String        reasonDetail  = "";
                    String        rawMatchWord  = "";
                    double        maxSimilarity = 0.0;
                    String        suspectedInsult = "";

                    outer:
                    for (int i = 0; i < normWords.length; i++) {
                        final String word    = normWords[i];
                        final String rawWord = (i < rawWords.length) ? rawWords[i] : word;

                        if (word.isEmpty() || rules.allowedWords().contains(word)) continue;

                        ViolationRule adultRule = cfg.rules().get(ViolationType.ADULT_CONTENT);
                        if (adultRule != null && adultRule.enabled()) {
                            for (String adult : rules.adultWords()) {
                                if (SimilarityChecker.getSimilarityRatio(word, adult, 0.0) >= rules.highThreshold()) {
                                    detectedType = ViolationType.ADULT_CONTENT;
                                    matchedWord  = adult;
                                    reasonDetail = "Adult content: " + adult;
                                    rawMatchWord = rawWord;
                                    break outer;
                                }
                            }
                        }

                        for (String insult : rules.insultWords()) {
                            double sim = SimilarityChecker.getSimilarityRatio(word, insult, 0.0);
                            if (sim > maxSimilarity) {
                                maxSimilarity   = sim;
                                suspectedInsult = insult;
                                rawMatchWord    = rawWord;
                                if (maxSimilarity >= rules.highThreshold()) break;
                            }
                        }

                        if (maxSimilarity >= rules.highThreshold()) {
                            matchedWord  = suspectedInsult;
                            reasonDetail = "Insult (fuzzy): " + matchedWord;
                            detectedType = hasFamilyContext(normWords, rules.familyWords(), i)
                                    ? ViolationType.FAMILY_INSULT : ViolationType.INSULT;
                            break outer;
                        }

                        if (rules.expressiveWords().contains(word)) {
                            boolean targeted =
                                    (i > 0 && PERSONAL_PRONOUNS.contains(normWords[i - 1])) ||
                                    (i < normWords.length - 1 && PERSONAL_PRONOUNS.contains(normWords[i + 1]));
                            if (targeted) {
                                matchedWord  = word;
                                rawMatchWord = rawWord;
                                reasonDetail = "Targeted profanity: " + word;
                                detectedType = hasFamilyContext(normWords, rules.familyWords(), i)
                                        ? ViolationType.FAMILY_INSULT : ViolationType.INSULT;
                                break outer;
                            }
                        }
                    }

                    if (detectedType != null) {
                        dispatch(plugin, player, punishmentManager, event, rawText, rawMatchWord, detectedType, reasonDetail, cfg, isChatMsg);

                    } else if (spamCandidate != null) {
                        dispatch(plugin, player, punishmentManager, event, rawText, rawText, ViolationType.SPAM, "Spam", cfg, isChatMsg);

                    } else if (capsCandidate != null) {
                        dispatch(plugin, player, punishmentManager, event, rawText, rawText, ViolationType.CAPS, "Caps", cfg, isChatMsg);

                    } else if (maxSimilarity >= rules.mediumThreshold()) {
                        suspiciousLogger.warning(String.format(
                                "[A.P.V.E.] Player: %s | Text: '%s' | Suspicion: '%s' (%.0f%%)",
                                player.getName(), rawText, suspectedInsult, maxSimilarity * 100));
                    }
                }
            }
        );

        Bukkit.getPluginManager().registerEvents(new Listener() {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncChatApve(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (pendingBlockMessages.remove(uuid)) {
            if (!event.isCancelled()) {
                event.setCancelled(true);
                apveCancelledMessages.add(uuid);
            }
            return;
        }

        String censoredMsg = pendingCensorMessages.remove(uuid);
        if (censoredMsg != null && !event.isCancelled()) {
            event.setMessage(censoredMsg);
        }
    }


    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncChatMonitor(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (event.isCancelled()) {
            if (!apveCancelledMessages.remove(uuid)) {

                externallyMutedPlayers.add(uuid);
            }

        } else {
            apveCancelledMessages.remove(uuid); 
            externallyMutedPlayers.remove(uuid);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        externallyMutedPlayers.remove(uuid);
        pendingBlockMessages.remove(uuid);
        pendingCensorMessages.remove(uuid);
        apveCancelledMessages.remove(uuid);
    }

   }, plugin);
}

    // ─── DISPATCH ────────────────────────────────────────────────────────
    private static void dispatch(Plugin plugin, Player player, PunishmentManager pm, PacketReceiveEvent event,
                                 String rawText, String badWord, ViolationType type, String reasonDetail, 
                                 GlobalConfig cfg, boolean isChatMsg) {
        
        ViolationRule rule = cfg.rules().get(type);
        if (rule == null || !rule.enabled()) return;


        String finalMessage = rawText;

            if (rule.censor() && !rule.block()) {
                if (type == ViolationType.CAPS) {
                finalMessage = finalMessage.toLowerCase();
                } else if (badWord != null && !badWord.isEmpty()) {
                finalMessage = finalMessage.replaceAll("(?i)" + Pattern.quote(badWord), "***");
                    if (finalMessage.equals(rawText)) finalMessage = "***";
                    } else {
                        finalMessage = "***";
                }
            }

final String fMsg = finalMessage;


if (isChatMsg) {
    if (rule.block()) {
        pendingBlockMessages.add(player.getUniqueId());
    } else if (rule.censor()) {
        pendingCensorMessages.put(player.getUniqueId(), fMsg);
    }
}

        Bukkit.getScheduler().runTask(plugin, () -> {

            if (externallyMutedPlayers.contains(player.getUniqueId())) {
                return;
            }

            boolean executePunishment = true;
            String warnMsgToSend = null;

            if (cfg.warnsIsEnabled() && cfg.warnLimitIsEnabled()) {
                UUID uuid = player.getUniqueId();
                StoredViolation currentViolation = new StoredViolation(type, rule, reasonDetail, badWord);

                highestViolations.compute(uuid, (k, old) -> {
                    if (old == null || type.getPriority() > old.type().getPriority()) {
                        return currentViolation;
                    }
                    return old;
                });

                int warns = warnCounts.getOrDefault(uuid, 0) + 1;
                warnCounts.put(uuid, warns);

                if (cfg.tempWarns()) {
                    long resetTicks = parseTimeToTicks(cfg.warnResetTime());
                    BukkitTask old = resetTasks.remove(uuid);
                    if (old != null) old.cancel();

                    BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        int current = warnCounts.getOrDefault(uuid, 0);
                        int newCount = Math.max(0, current - cfg.warnResetCount());
                        if (newCount == 0) {
                            warnCounts.remove(uuid);
                            highestViolations.remove(uuid);
                        } else {
                            warnCounts.put(uuid, newCount);
                        }

                        highestViolations.remove(uuid);
                        spamHistory.remove(uuid);
                        resetTasks.remove(uuid);
                    }, resetTicks);
                    resetTasks.put(uuid, task);
                }

                if (warns <= cfg.warnLimit()) {
                    executePunishment = false;
                    warnMsgToSend = warns < cfg.warnLimit() ? cfg.warnMessage() : cfg.lastWarnMessage();
                    plugin.getLogger().info(logLine("WARN", warns + "/" + cfg.warnLimit(), player.getName(), reasonDetail, badWord));
                } else {
                    warnCounts.put(uuid, 0);
                }
            }

            
            if (rule.block()) {
                if (!externallyMutedPlayers.contains(player.getUniqueId())) {
                sendMultilineMessage(player, rule.blockReason());
             }
            } else if (rule.censor() && isChatMsg) {
                if (!externallyMutedPlayers.contains(player.getUniqueId())) {
                sendMultilineMessage(player, rule.censorReason());
            }
        }

            if (warnMsgToSend != null && !externallyMutedPlayers.contains(player.getUniqueId())) {
                sendMultilineMessage(player, warnMsgToSend);
            }

            if (executePunishment && rule.punishEnabled()) {
                StoredViolation heaviest = (cfg.warnsIsEnabled() && cfg.warnLimitIsEnabled())
                        ? highestViolations.remove(player.getUniqueId())
                        : null;

                if (heaviest != null) {
                    applyPunishment(plugin, player, pm, heaviest.rule(), heaviest.reasonDetail(), heaviest.badWord());
                } else {
                    applyPunishment(plugin, player, pm, rule, reasonDetail, badWord);
                }
            }
        });
    }

    public static String colorize(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    // ─── PUNISH ──────────────────────────────────────────────────────────
    private static void applyPunishment(Plugin plugin, Player player, PunishmentManager pm, ViolationRule rule, String detail, String word) {
        switch (rule.type().toLowerCase()) {
            case "mute" -> pm.mutePlayer(player.getUniqueId(), rule.reason(), rule.duration());
            case "ban" -> pm.banPlayer(player.getUniqueId(), rule.reason(), rule.duration());
            case "banip" -> {
                String ip = (player.getAddress() != null && player.getAddress().getAddress() != null)
                        ? player.getAddress().getAddress().getHostAddress() : "127.0.0.1";
                pm.banipPlayer(ip, rule.reason(), rule.duration());
            }
            case "kick" -> pm.kickPlayer(player.getUniqueId(), rule.reason());
            case "none" -> { /* nothing */ }
        }
        
        if (!rule.type().equalsIgnoreCase("none")) {
            plugin.getLogger().info(logLine(rule.type().toUpperCase(), rule.duration(), player.getName(), detail, word));
        }
    }

    // ─── HELPERS ─────────────────────────────────────────────────────────
    private static boolean hasFamilyContext(String[] normWords, Set<String> familyWords, int insultIndex) {
        if (insultIndex < 0) return false;
        for (int i = 0; i < normWords.length; i++) {
            if (i == insultIndex) continue;               
            if (Math.abs(i - insultIndex) > 4) continue;  
            String word = normWords[i];
            for (String fw : familyWords) {
                if (word.equals(fw) || word.startsWith(fw)) return true;
            }
        }
        return false;
    }

    private static boolean checkAndRecordSpam(UUID id, String normalizedText, int maxCount, long windowMs, double threshold) {
        long now = System.currentTimeMillis();
        Deque<SpamEntry> history = spamHistory.computeIfAbsent(id, k -> new ArrayDeque<>());
        history.removeIf(e -> (now - e.timestamp) > windowMs);
        int similarCount = 0;
        for (SpamEntry e : history) {
            if (SimilarityChecker.getSimilarityRatio(e.normalizedText, normalizedText, 0.0) >= threshold) {
                if (++similarCount >= maxCount) return true;
            }
        }
        history.addLast(new SpamEntry(normalizedText, now));
        return false;
    }

    private static long parseTimeToTicks(String timeStr) {
        if (timeStr == null || timeStr.isEmpty()) return 30 * 60 * 20L;
        timeStr = timeStr.toLowerCase();
        try {
            if (timeStr.endsWith("h")) return Long.parseLong(timeStr, 0, timeStr.length() - 1, 10) * 60 * 60 * 20L;
            if (timeStr.endsWith("m")) return Long.parseLong(timeStr, 0, timeStr.length() - 1, 10) * 60 * 20L;
            if (timeStr.endsWith("s")) return Long.parseLong(timeStr, 0, timeStr.length() - 1, 10) * 20L;
        } catch (NumberFormatException ignored) {}
        return 30 * 60 * 20L;
    }

    private static boolean isCaps(String rawText, int minLength, int minPct) {
        String letters = NON_LETTER_PATTERN.matcher(rawText).replaceAll("");
        if (letters.length() < minLength) return false;
        long upper = letters.chars().filter(Character::isUpperCase).count();
        return (double) upper / letters.length() * 100.0 >= minPct;
    }

    private static void sendMultilineMessage(Player player, String message) {
        String colored = colorize((message == null ? "" : message).trim());
        for (String line : NEWLINE_PATTERN.split(colored)) {
            String t = line.trim();
            if (!t.isEmpty()) player.sendMessage(t);
        }
    }

    private static String logLine(String action, String duration, String name, String detail, String word) {
        return "[A.P.V.E.] " + action + " " + duration + " → " + name + " [" + detail + (word.isEmpty() ? "" : " | '" + word + "'") + "]";
    }

    // ─── PACKET EXTRACTION ───────────────────────────────────────────────
    private static String extractRawText(PacketReceiveEvent event, boolean isChatMsg, boolean isChatCmd, Set<String> interceptedCommands) {
        if (isChatMsg) {
            return new WrapperPlayClientChatMessage(event).getMessage();
        }
        if (isChatCmd) {
            WrapperPlayClientChatCommand wrapper = new WrapperPlayClientChatCommand(event);
            String command = wrapper.getCommand(); 
            if (command == null || command.isEmpty()) return "";
            String[] parts = command.split(" ", 2);
            String cmd  = parts[0].toLowerCase();
            String args = parts.length > 1 ? parts[1] : "";
            if (interceptedCommands.contains(cmd)) return extractMessageFromArgs(cmd, args);
        }
        return "";
    }

    private static boolean containsIP(String text) {
        return text != null && !text.isEmpty() && IP_PATTERN.matcher(text).find();
    }

    private static String extractMessageFromArgs(String command, String args) {
        if (args == null || args.isEmpty()) return "";
        if (command.equalsIgnoreCase("r") || command.equalsIgnoreCase("reply")) return args;
        String[] parts = args.split(" ", 2);
        return parts.length > 1 ? parts[1] : "";
    }
}