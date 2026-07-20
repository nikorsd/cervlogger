package net.cervlogger.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import net.cervlogger.command.Params;

/**
 * Read-side of the database: builds and runs lookup queries for
 * /cl lookup, /cl rollback, /cl restore and the inspector.
 */
public final class Lookup {

    /** One result row; unused fields are null/0 depending on the table. */
    public record Row(long rowid, long time, String user, String world, int x, int y, int z,
                      int action, String type, String oldType, int amount, boolean rolledBack) {
    }

    private Lookup() {
    }

    private static boolean hasCoords(Params.LogTable table) {
        return table != Params.LogTable.CHAT && table != Params.LogTable.COMMAND;
    }

    /**
     * Runs a lookup for the given params. Call from an async thread only.
     *
     * @param rolledBackFilter null = any, 0 = not rolled back, 1 = rolled back
     * @param limit            max rows, or -1 for unlimited
     */
    public static List<Row> lookup(Params params, Integer rolledBackFilter, int limit, int offset, boolean oldestFirst) throws SQLException {
        synchronized (Database.lock()) {
            Connection conn = Database.connection();
            if (conn == null) {
                throw new SQLException("Database is not ready");
            }
            StringBuilder sql = new StringBuilder("SELECT rowid, ").append(columns(params.table))
                    .append(" FROM ").append(params.table.tableName);
            List<Object> args = new ArrayList<>();
            sql.append(where(params, rolledBackFilter, args, conn));
            sql.append(" ORDER BY time ").append(oldestFirst ? "ASC" : "DESC").append(", rowid ").append(oldestFirst ? "ASC" : "DESC");
            if (limit >= 0) {
                sql.append(" LIMIT ? OFFSET ?");
                args.add(limit);
                args.add(offset);
            }
            try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    return readRows(conn, rs, params.table);
                }
            }
        }
    }

    /** Counts matching rows. Call from an async thread only. */
    public static int count(Params params, Integer rolledBackFilter) throws SQLException {
        synchronized (Database.lock()) {
            Connection conn = Database.connection();
            if (conn == null) {
                throw new SQLException("Database is not ready");
            }
            List<Object> args = new ArrayList<>();
            String sql = "SELECT COUNT(*) FROM " + params.table.tableName + where(params, rolledBackFilter, args, conn);
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                bind(ps, args);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getInt(1) : 0;
                }
            }
        }
    }

    /** Block/container/sign history at one exact position, for the inspector. */
    public static List<Row> blockHistory(String world, int x, int y, int z, String tableName, int limit) throws SQLException {
        synchronized (Database.lock()) {
            Connection conn = Database.connection();
            if (conn == null) {
                throw new SQLException("Database is not ready");
            }
            Params.LogTable table = tableFor(tableName);
            String sql = "SELECT rowid, " + columns(table) + " FROM " + tableName
                    + " WHERE wid = (SELECT rowid FROM co_world WHERE world = ?) AND x = ? AND y = ? AND z = ?"
                    + " ORDER BY time DESC, rowid DESC LIMIT ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, world);
                ps.setInt(2, x);
                ps.setInt(3, y);
                ps.setInt(4, z);
                ps.setInt(5, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    return readRows(conn, rs, table);
                }
            }
        }
    }

    /** Marks rows as rolled back / restored. Call from an async thread only. */
    public static void setRolledBack(String tableName, List<Long> rowids, boolean rolledBack) throws SQLException {
        if (rowids.isEmpty()) {
            return;
        }
        synchronized (Database.lock()) {
            Connection conn = Database.connection();
            if (conn == null) {
                return;
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE " + tableName + " SET rolled_back = ? WHERE rowid = ?")) {
                for (Long rowid : rowids) {
                    ps.setInt(1, rolledBack ? 1 : 0);
                    ps.setLong(2, rowid);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        }
    }

    private static Params.LogTable tableFor(String tableName) {
        for (Params.LogTable table : Params.LogTable.values()) {
            if (table.tableName.equals(tableName)) {
                return table;
            }
        }
        return Params.LogTable.BLOCK;
    }

    private static String columns(Params.LogTable table) {
        return switch (table) {
            case BLOCK -> "time, user, wid, x, y, z, action, type, old_type, rolled_back";
            case CONTAINER -> "time, user, wid, x, y, z, action, type, amount, rolled_back";
            case KILL -> "time, user, wid, x, y, z, type, rolled_back";
            case SIGN -> "time, user, wid, x, y, z, text";
            case SESSION -> "time, user, wid, x, y, z, action";
            case CHAT, COMMAND -> "time, user, message";
        };
    }

    private static String where(Params params, Integer rolledBackFilter, List<Object> args, Connection conn) throws SQLException {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (params.since > 0) {
            where.append(" AND time >= ?");
            args.add(params.since);
        }
        if (!params.users.isEmpty()) {
            where.append(" AND user IN (SELECT rowid FROM co_user WHERE user IN (")
                    .append(placeholders(params.users.size()))
                    .append(Database.isMySql() ? "))" : ") COLLATE NOCASE)");
            args.addAll(params.users);
        }
        if (hasCoords(params.table)) {
            if (params.hasRadius()) {
                where.append(" AND x BETWEEN ? AND ? AND z BETWEEN ? AND ? AND y BETWEEN ? AND ?");
                args.add(params.center.getX() - params.radius);
                args.add(params.center.getX() + params.radius);
                args.add(params.center.getZ() - params.radius);
                args.add(params.center.getZ() + params.radius);
                args.add(params.center.getY() - params.radius);
                args.add(params.center.getY() + params.radius);
            }
            if (!params.isGlobal()) {
                where.append(" AND wid = (SELECT rowid FROM co_world WHERE world = ?)");
                args.add(params.world);
            }
        }
        if (!params.actions.isEmpty() && (params.table == Params.LogTable.BLOCK || params.table == Params.LogTable.CONTAINER || params.table == Params.LogTable.SESSION)) {
            where.append(" AND action IN (").append(placeholders(params.actions.size())).append(")");
            args.addAll(params.actions);
        }
        boolean typed = params.table == Params.LogTable.BLOCK || params.table == Params.LogTable.CONTAINER || params.table == Params.LogTable.KILL;
        if (typed && !params.include.isEmpty()) {
            // co_block.type may include block state properties -> match on the base id.
            where.append(" AND (CASE WHEN instr(type, '[') > 0 THEN substr(type, 1, instr(type, '[') - 1) ELSE type END) IN (")
                    .append(placeholders(params.include.size())).append(")");
            args.addAll(params.include);
        }
        if (typed && !params.exclude.isEmpty()) {
            where.append(" AND (CASE WHEN instr(type, '[') > 0 THEN substr(type, 1, instr(type, '[') - 1) ELSE type END) NOT IN (")
                    .append(placeholders(params.exclude.size())).append(")");
            args.addAll(params.exclude);
        }
        if (rolledBackFilter != null && (params.table == Params.LogTable.BLOCK || params.table == Params.LogTable.CONTAINER || params.table == Params.LogTable.KILL)) {
            where.append(" AND rolled_back = ?");
            args.add(rolledBackFilter);
        }
        return where.toString();
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        int index = 1;
        for (Object arg : args) {
            if (arg instanceof Integer i) {
                ps.setInt(index++, i);
            }
            else if (arg instanceof Long l) {
                ps.setLong(index++, l);
            }
            else {
                ps.setString(index++, String.valueOf(arg));
            }
        }
    }

    private static List<Row> readRows(Connection conn, ResultSet rs, Params.LogTable table) throws SQLException {
        List<Row> rows = new ArrayList<>();
        while (rs.next()) {
            long rowid = rs.getLong("rowid");
            long time = rs.getLong("time");
            String user = Database.userName(conn, rs.getInt("user"));
            String world = null;
            int x = 0;
            int y = 0;
            int z = 0;
            if (hasCoords(table)) {
                world = Database.worldName(conn, rs.getInt("wid"));
                x = rs.getInt("x");
                y = rs.getInt("y");
                z = rs.getInt("z");
            }
            int action = 0;
            String type = null;
            String oldType = null;
            int amount = 0;
            boolean rolledBack = false;
            switch (table) {
                case BLOCK -> {
                    action = rs.getInt("action");
                    type = rs.getString("type");
                    oldType = rs.getString("old_type");
                    rolledBack = rs.getInt("rolled_back") == 1;
                }
                case CONTAINER -> {
                    action = rs.getInt("action");
                    type = rs.getString("type");
                    amount = rs.getInt("amount");
                    rolledBack = rs.getInt("rolled_back") == 1;
                }
                case KILL -> {
                    type = rs.getString("type");
                    rolledBack = rs.getInt("rolled_back") == 1;
                }
                case SIGN -> type = rs.getString("text");
                case SESSION -> action = rs.getInt("action");
                case CHAT, COMMAND -> type = rs.getString("message");
            }
            rows.add(new Row(rowid, time, user, world, x, y, z, action, type, oldType, amount, rolledBack));
        }
        return rows;
    }
}
