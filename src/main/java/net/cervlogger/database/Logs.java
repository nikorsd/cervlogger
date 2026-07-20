package net.cervlogger.database;

import java.sql.PreparedStatement;

import net.cervlogger.util.TimeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Static logging entry points used by the event listeners.
 * Every method captures primitives and enqueues an async insert.
 *
 * Block actions: 0=break/remove, 1=place, 2=interact, 3=explosion, 4=burn
 * Container actions: 0=removed, 1=added
 * Session actions: 0=logout, 1=login
 */
public final class Logs {

    private Logs() {
    }

    public static String worldName(Level level) {
        return level.dimension().location().toString();
    }

    public static void logBlock(String user, Level level, BlockPos pos, int action, String type, String oldType) {
        long time = TimeUtils.now();
        String world = worldName(level);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, null);
            int wid = Database.worldId(conn, world);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO co_block(time, user, wid, x, y, z, action, type, old_type, rolled_back) VALUES (?,?,?,?,?,?,?,?,?,0)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setInt(3, wid);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setInt(7, action);
                ps.setString(8, type);
                ps.setString(9, oldType);
                ps.executeUpdate();
            }
        });
    }

    public static void logContainer(String user, Level level, BlockPos pos, int action, String itemId, int amount) {
        long time = TimeUtils.now();
        String world = worldName(level);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, null);
            int wid = Database.worldId(conn, world);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO co_container(time, user, wid, x, y, z, action, type, amount, rolled_back) VALUES (?,?,?,?,?,?,?,?,?,0)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setInt(3, wid);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setInt(7, action);
                ps.setString(8, itemId);
                ps.setInt(9, amount);
                ps.executeUpdate();
            }
        });
    }

    public static void logEntityKill(String user, Level level, BlockPos pos, String entityType) {
        long time = TimeUtils.now();
        String world = worldName(level);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, null);
            int wid = Database.worldId(conn, world);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO co_entity_kill(time, user, wid, x, y, z, type, rolled_back) VALUES (?,?,?,?,?,?,?,0)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setInt(3, wid);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setString(7, entityType);
                ps.executeUpdate();
            }
        });
    }

    public static void logSign(String user, Level level, BlockPos pos, String text) {
        long time = TimeUtils.now();
        String world = worldName(level);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, null);
            int wid = Database.worldId(conn, world);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO co_sign(time, user, wid, x, y, z, text) VALUES (?,?,?,?,?,?,?)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setInt(3, wid);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setString(7, text);
                ps.executeUpdate();
            }
        });
    }

    public static void logChat(String user, String message) {
        long time = TimeUtils.now();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, null);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO co_chat(time, user, message) VALUES (?,?,?)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setString(3, message);
                ps.executeUpdate();
            }
        });
    }

    public static void logCommand(String user, String message) {
        long time = TimeUtils.now();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, null);
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO co_command(time, user, message) VALUES (?,?,?)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setString(3, message);
                ps.executeUpdate();
            }
        });
    }

    public static void logSession(String user, String uuid, Level level, BlockPos pos, int action) {
        long time = TimeUtils.now();
        String world = worldName(level);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        ConsumerThread.queue(conn -> {
            int uid = Database.userId(conn, user, uuid);
            int wid = Database.worldId(conn, world);
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO co_session(time, user, wid, x, y, z, action) VALUES (?,?,?,?,?,?,?)")) {
                ps.setLong(1, time);
                ps.setInt(2, uid);
                ps.setInt(3, wid);
                ps.setInt(4, x);
                ps.setInt(5, y);
                ps.setInt(6, z);
                ps.setInt(7, action);
                ps.executeUpdate();
            }
        });
    }
}
