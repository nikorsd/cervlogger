package net.cervlogger.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import net.minecraft.server.MinecraftServer;

/**
 * Simple properties-based configuration, stored in config/cervlogger/config.properties.
 * Mirrors the most important toggles of the original CervLogger config.yml.
 */
public final class ConfigHandler {

    public static boolean logBlocks = true;
    public static boolean logContainers = true;
    public static boolean logInteractions = true;
    public static boolean logEntityKills = true;
    public static boolean logExplosions = true;
    public static boolean logChat = true;
    public static boolean logCommands = true;
    public static boolean logSessions = true;
    public static boolean logSigns = true;
    public static boolean logBuckets = true;
    public static boolean logNonPlayerActions = true;

    /** Substrings of block ids whose right-clicks are always logged (e.g. the Create Aeronautics physics assembler). */
    public static Set<String> watchedInteractBlocks = new HashSet<>(Set.of("physics_assembler"));

    /** Storage backend: "sqlite" (default) or "mysql". */
    public static String storageType = "sqlite";
    public static String mysqlHost = "127.0.0.1";
    public static int mysqlPort = 3306;
    public static String mysqlDatabase = "cervlogger";
    public static String mysqlUsername = "root";
    public static String mysqlPassword = "";

    public static int defaultRadius = 10;
    public static int maxRadius = 250;
    public static int lookupPageSize = 7;

    private static Path dataDir;

    private ConfigHandler() {
    }

    public static void load(MinecraftServer server) throws IOException {
        dataDir = server.getServerDirectory().resolve("config").resolve("cervlogger");
        Files.createDirectories(dataDir);
        Path file = dataDir.resolve("config.properties");

        Properties props = new Properties();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                props.load(in);
            }
        }

        logBlocks = getBool(props, "log-blocks", logBlocks);
        logContainers = getBool(props, "log-containers", logContainers);
        logInteractions = getBool(props, "log-interactions", logInteractions);
        logEntityKills = getBool(props, "log-entity-kills", logEntityKills);
        logExplosions = getBool(props, "log-explosions", logExplosions);
        logChat = getBool(props, "log-chat", logChat);
        logCommands = getBool(props, "log-commands", logCommands);
        logSessions = getBool(props, "log-sessions", logSessions);
        logSigns = getBool(props, "log-signs", logSigns);
        logBuckets = getBool(props, "log-buckets", logBuckets);
        logNonPlayerActions = getBool(props, "log-non-player-actions", logNonPlayerActions);
        watchedInteractBlocks = parseList(props.getProperty("log-interact-block-ids", "physics_assembler"));
        storageType = props.getProperty("storage-type", storageType).trim().toLowerCase(Locale.ROOT);
        mysqlHost = props.getProperty("mysql-host", mysqlHost).trim();
        mysqlPort = getInt(props, "mysql-port", mysqlPort);
        mysqlDatabase = props.getProperty("mysql-database", mysqlDatabase).trim();
        mysqlUsername = props.getProperty("mysql-username", mysqlUsername).trim();
        mysqlPassword = props.getProperty("mysql-password", mysqlPassword);
        defaultRadius = getInt(props, "default-radius", defaultRadius);
        maxRadius = getInt(props, "max-radius", maxRadius);
        lookupPageSize = getInt(props, "lookup-page-size", lookupPageSize);

        // Write the file back so all keys (including defaults) are visible to the admin.
        props.setProperty("log-blocks", String.valueOf(logBlocks));
        props.setProperty("log-containers", String.valueOf(logContainers));
        props.setProperty("log-interactions", String.valueOf(logInteractions));
        props.setProperty("log-entity-kills", String.valueOf(logEntityKills));
        props.setProperty("log-explosions", String.valueOf(logExplosions));
        props.setProperty("log-chat", String.valueOf(logChat));
        props.setProperty("log-commands", String.valueOf(logCommands));
        props.setProperty("log-sessions", String.valueOf(logSessions));
        props.setProperty("log-signs", String.valueOf(logSigns));
        props.setProperty("log-buckets", String.valueOf(logBuckets));
        props.setProperty("log-non-player-actions", String.valueOf(logNonPlayerActions));
        props.setProperty("log-interact-block-ids", String.join(",", watchedInteractBlocks));
        props.setProperty("storage-type", storageType);
        props.setProperty("mysql-host", mysqlHost);
        props.setProperty("mysql-port", String.valueOf(mysqlPort));
        props.setProperty("mysql-database", mysqlDatabase);
        props.setProperty("mysql-username", mysqlUsername);
        props.setProperty("mysql-password", mysqlPassword);
        props.setProperty("default-radius", String.valueOf(defaultRadius));
        props.setProperty("max-radius", String.valueOf(maxRadius));
        props.setProperty("lookup-page-size", String.valueOf(lookupPageSize));

        try (OutputStream out = Files.newOutputStream(file)) {
            props.store(out, "CervLogger (NeoForge) configuration");
        }
    }

    public static Path dataDir() {
        return dataDir;
    }

    /** True if right-clicks on this block id should always be logged as interactions. */
    public static boolean isWatchedInteractBlock(String blockId) {
        for (String watched : watchedInteractBlocks) {
            if (!watched.isEmpty() && blockId.contains(watched)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> parseList(String value) {
        Set<String> result = new HashSet<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static boolean getBool(Properties props, String key, boolean def) {
        String value = props.getProperty(key);
        return value == null ? def : Boolean.parseBoolean(value.trim());
    }

    private static int getInt(Properties props, String key, int def) {
        String value = props.getProperty(key);
        if (value == null) {
            return def;
        }
        try {
            return Integer.parseInt(value.trim());
        }
        catch (NumberFormatException e) {
            return def;
        }
    }
}
