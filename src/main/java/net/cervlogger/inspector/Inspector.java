package net.cervlogger.inspector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.cervlogger.CervLogger;
import net.cervlogger.database.Lookup;
import net.cervlogger.util.Chat;
import net.cervlogger.util.TimeUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * The inspector ("/cl i") mode: left/right-clicking blocks shows their history,
 * replicating CervLogger's inspector behaviour.
 */
public final class Inspector {

    private static final Set<UUID> INSPECTING = ConcurrentHashMap.newKeySet();

    /** Timestamp (ms) of each player's last inspector lookup, used for rate limiting. */
    private static final Map<UUID, Long> LAST_LOOKUP = new ConcurrentHashMap<>();
    private static final long COOLDOWN_MS = 500;

    private Inspector() {
    }

    /**
     * Rate limiter for inspector clicks: returns true (and arms the cooldown) if the
     * player may run a lookup now, false if the previous one was less than ~0.5s ago.
     */
    public static boolean tryAcquireCooldown(Player player) {
        long now = System.currentTimeMillis();
        Long last = LAST_LOOKUP.get(player.getUUID());
        if (last != null && now - last < COOLDOWN_MS) {
            return false;
        }
        LAST_LOOKUP.put(player.getUUID(), now);
        return true;
    }

    public static boolean isInspecting(Player player) {
        return INSPECTING.contains(player.getUUID());
    }

    public static void toggle(ServerPlayer player) {
        if (INSPECTING.remove(player.getUUID())) {
            Chat.sendPrefixed(player, Chat.text("Inspector now disabled.", ChatFormatting.WHITE));
        }
        else {
            INSPECTING.add(player.getUUID());
            Chat.sendPrefixed(player, Chat.text("Inspector now enabled.", ChatFormatting.WHITE));
        }
    }

    public static void remove(Player player) {
        INSPECTING.remove(player.getUUID());
        LAST_LOOKUP.remove(player.getUUID());
    }

    /** Queries and prints the history of a single block position. Safe to call from the main thread. */
    public static void showHistory(ServerPlayer player, ServerLevel level, BlockPos pos) {
        final String world = level.dimension().location().toString();
        final int x = pos.getX();
        final int y = pos.getY();
        final int z = pos.getZ();
        Thread thread = new Thread(() -> {
            try {
                List<Lookup.Row> blocks = Lookup.blockHistory(world, x, y, z, "co_block", 7);
                List<Lookup.Row> containers = Lookup.blockHistory(world, x, y, z, "co_container", 7);
                List<Lookup.Row> signs = Lookup.blockHistory(world, x, y, z, "co_sign", 3);
                player.server.execute(() -> {
                    Chat.send(player, Chat.text("---- ", ChatFormatting.DARK_GRAY)
                            .append(Chat.text("CervLogger", ChatFormatting.DARK_AQUA))
                            .append(Chat.text(" ---- (x" + x + "/y" + y + "/z" + z + ")", ChatFormatting.DARK_GRAY)));
                    if (blocks.isEmpty() && containers.isEmpty() && signs.isEmpty()) {
                        Chat.send(player, Chat.text("No block data found for this location.", ChatFormatting.WHITE));
                        return;
                    }
                    // Merge all tables into one list sorted newest-first, so container
                    // and sign entries interleave correctly with block entries.
                    record Entry(Lookup.Row row, int kind) {
                    }
                    List<Entry> merged = new ArrayList<>();
                    blocks.forEach(r -> merged.add(new Entry(r, 0)));
                    containers.forEach(r -> merged.add(new Entry(r, 1)));
                    signs.forEach(r -> merged.add(new Entry(r, 2)));
                    merged.sort((a, b) -> {
                        int cmp = Long.compare(b.row().time(), a.row().time());
                        return cmp != 0 ? cmp : Long.compare(b.row().rowid(), a.row().rowid());
                    });
                    for (Entry entry : merged) {
                        Chat.send(player, switch (entry.kind()) {
                            case 0 -> formatBlockRow(entry.row());
                            case 1 -> formatContainerRow(entry.row());
                            default -> formatSignRow(entry.row());
                        });
                    }
                });
            }
            catch (Exception e) {
                CervLogger.LOGGER.error("[CervLogger] Inspector lookup failed", e);
            }
        }, "CervLogger-Inspector");
        thread.setDaemon(true);
        thread.start();
    }

    public static MutableComponent formatBlockRow(Lookup.Row row) {
        String verb = switch (row.action()) {
            case 0 -> "broke";
            case 1 -> "placed";
            case 2 -> "clicked";
            case 3 -> "blew up";
            case 4 -> "burned";
            default -> "changed";
        };
        MutableComponent line = Chat.text(TimeUtils.formatAgo(row.time()) + " ago ", ChatFormatting.GRAY)
                .append(Chat.text("- ", ChatFormatting.WHITE))
                .append(Chat.text(row.user(), ChatFormatting.DARK_AQUA))
                .append(Chat.text(" " + verb + " ", ChatFormatting.WHITE))
                .append(Chat.text(shortType(row.type()), ChatFormatting.DARK_AQUA));
        if (row.rolledBack()) {
            line.append(Chat.text(" (rolled back)", ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH));
        }
        return line;
    }

    public static MutableComponent formatContainerRow(Lookup.Row row) {
        String verb = row.action() == 1 ? "added" : "removed";
        MutableComponent line = Chat.text(TimeUtils.formatAgo(row.time()) + " ago ", ChatFormatting.GRAY)
                .append(Chat.text("- ", ChatFormatting.WHITE))
                .append(Chat.text(row.user(), ChatFormatting.DARK_AQUA))
                .append(Chat.text(" " + verb + " ", ChatFormatting.WHITE))
                .append(Chat.text("x" + row.amount() + " ", ChatFormatting.WHITE))
                .append(Chat.text(shortType(row.type()), ChatFormatting.DARK_AQUA));
        if (row.rolledBack()) {
            line.append(Chat.text(" (rolled back)", ChatFormatting.GRAY, ChatFormatting.STRIKETHROUGH));
        }
        return line;
    }

    public static MutableComponent formatSignRow(Lookup.Row row) {
        return Chat.text(TimeUtils.formatAgo(row.time()) + " ago ", ChatFormatting.GRAY)
                .append(Chat.text("- ", ChatFormatting.WHITE))
                .append(Chat.text(row.user(), ChatFormatting.DARK_AQUA))
                .append(Chat.text(" edited sign: ", ChatFormatting.WHITE))
                .append(Chat.text("\"" + row.type() + "\"", ChatFormatting.GRAY));
    }

    public static Component header(int x, int y, int z) {
        return Chat.text("---- ", ChatFormatting.DARK_GRAY)
                .append(Chat.text("CervLogger", ChatFormatting.DARK_AQUA))
                .append(Chat.text(" ---- (x" + x + "/y" + y + "/z" + z + ")", ChatFormatting.DARK_GRAY));
    }

    private static String shortType(String type) {
        if (type == null) {
            return "unknown";
        }
        int bracket = type.indexOf('[');
        String base = bracket >= 0 ? type.substring(0, bracket) : type;
        return base.startsWith("minecraft:") ? base.substring("minecraft:".length()) : base;
    }
}
