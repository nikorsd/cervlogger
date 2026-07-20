package net.cervlogger.command;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.cervlogger.config.ConfigHandler;
import net.cervlogger.util.BlockUtils;
import net.cervlogger.util.TimeUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;

/**
 * Parser for CervLogger-style command parameters:
 *   u:&lt;user&gt; t:&lt;time&gt; r:&lt;radius&gt; a:&lt;action&gt; b:&lt;blocks&gt; e:&lt;exclude&gt; w:&lt;world&gt; p:&lt;page&gt;
 *
 * Examples:
 *   /cl lookup u:Notch t:1h r:20
 *   /cl rollback u:griefer t:2d r:30 a:-block
 *   /cl lookup a:container t:1w b:diamond
 */
public final class Params {

    public enum LogTable {
        BLOCK("co_block"),
        CONTAINER("co_container"),
        KILL("co_entity_kill"),
        SIGN("co_sign"),
        CHAT("co_chat"),
        COMMAND("co_command"),
        SESSION("co_session");

        public final String tableName;

        LogTable(String tableName) {
            this.tableName = tableName;
        }
    }

    public LogTable table = LogTable.BLOCK;
    public final Set<String> users = new HashSet<>();
    public long since = 0; // epoch seconds, 0 = unbounded
    public int radius = Integer.MIN_VALUE; // MIN_VALUE = not specified, -1 = #global
    public BlockPos center;
    public String world;
    public final Set<Integer> actions = new HashSet<>(); // empty = any action of the table
    public final Set<String> include = new HashSet<>();
    public final Set<String> exclude = new HashSet<>();
    public int page = 1;

    private Params() {
    }

    public boolean hasRadius() {
        return radius != Integer.MIN_VALUE && radius != -1;
    }

    public boolean isGlobal() {
        return radius == -1;
    }

    public static Params parse(CommandSourceStack source, String input) throws IllegalArgumentException {
        Params params = new Params();
        params.center = BlockPos.containing(source.getPosition());
        params.world = source.getLevel().dimension().location().toString();

        for (String rawToken : input.trim().split("\\s+")) {
            if (rawToken.isEmpty()) {
                continue;
            }
            int colon = rawToken.indexOf(':');
            if (colon <= 0) {
                throw new IllegalArgumentException("Invalid parameter: \"" + rawToken + "\"");
            }
            String key = rawToken.substring(0, colon).toLowerCase(Locale.ROOT);
            String value = rawToken.substring(colon + 1);
            if (value.isEmpty()) {
                throw new IllegalArgumentException("Missing value for parameter: \"" + rawToken + "\"");
            }
            switch (key) {
                case "u", "user", "users" -> {
                    for (String user : value.split(",")) {
                        if (!user.isEmpty()) {
                            params.users.add(user);
                        }
                    }
                }
                case "t", "time" -> params.since = TimeUtils.now() - TimeUtils.parseSeconds(value);
                case "r", "radius" -> {
                    if (value.equalsIgnoreCase("#global") || value.equalsIgnoreCase("global")) {
                        params.radius = -1;
                    }
                    else {
                        int radius;
                        try {
                            radius = Integer.parseInt(value);
                        }
                        catch (NumberFormatException e) {
                            throw new IllegalArgumentException("Invalid radius: \"" + value + "\"");
                        }
                        if (radius < 0) {
                            throw new IllegalArgumentException("Invalid radius: \"" + value + "\"");
                        }
                        if (radius > ConfigHandler.maxRadius) {
                            throw new IllegalArgumentException("The maximum radius is " + ConfigHandler.maxRadius + ".");
                        }
                        params.radius = radius;
                    }
                }
                case "a", "action" -> applyAction(params, value.toLowerCase(Locale.ROOT));
                case "b", "block", "blocks", "i", "item", "items", "include" -> {
                    for (String id : value.split(",")) {
                        if (!id.isEmpty()) {
                            params.include.add(BlockUtils.normalizeId(id));
                        }
                    }
                }
                case "e", "exclude" -> {
                    for (String id : value.split(",")) {
                        if (!id.isEmpty()) {
                            params.exclude.add(BlockUtils.normalizeId(id));
                        }
                    }
                }
                case "w", "world" -> {
                    String world = value.toLowerCase(Locale.ROOT);
                    switch (world) {
                        case "world", "overworld", "#overworld" -> params.world = "minecraft:overworld";
                        case "nether", "#nether", "world_nether" -> params.world = "minecraft:the_nether";
                        case "end", "#end", "world_the_end" -> params.world = "minecraft:the_end";
                        default -> params.world = world.contains(":") ? world : "minecraft:" + world;
                    }
                }
                case "p", "page" -> {
                    try {
                        params.page = Math.max(1, Integer.parseInt(value));
                    }
                    catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid page: \"" + value + "\"");
                    }
                }
                default -> throw new IllegalArgumentException("Invalid parameter: \"" + rawToken + "\"");
            }
        }
        return params;
    }

    private static void applyAction(Params params, String value) {
        switch (value) {
            case "block" -> {
                params.table = LogTable.BLOCK;
                params.actions.add(0);
                params.actions.add(1);
            }
            case "+block" -> {
                params.table = LogTable.BLOCK;
                params.actions.add(1);
            }
            case "-block" -> {
                params.table = LogTable.BLOCK;
                params.actions.add(0);
            }
            case "click", "interact" -> {
                params.table = LogTable.BLOCK;
                params.actions.add(2);
            }
            case "explosion" -> {
                params.table = LogTable.BLOCK;
                params.actions.add(3);
            }
            case "burn", "fire" -> {
                params.table = LogTable.BLOCK;
                params.actions.add(4);
            }
            case "container" -> params.table = LogTable.CONTAINER;
            case "+container" -> {
                params.table = LogTable.CONTAINER;
                params.actions.add(1);
            }
            case "-container" -> {
                params.table = LogTable.CONTAINER;
                params.actions.add(0);
            }
            case "kill", "kills" -> params.table = LogTable.KILL;
            case "sign", "signs" -> params.table = LogTable.SIGN;
            case "chat" -> params.table = LogTable.CHAT;
            case "command", "commands" -> params.table = LogTable.COMMAND;
            case "session", "sessions" -> params.table = LogTable.SESSION;
            case "+session", "login" -> {
                params.table = LogTable.SESSION;
                params.actions.add(1);
            }
            case "-session", "logout" -> {
                params.table = LogTable.SESSION;
                params.actions.add(0);
            }
            default -> throw new IllegalArgumentException("Invalid action: \"" + value + "\"");
        }
    }
}
