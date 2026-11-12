package lk.technocom.util;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small JDBC helper. All query methods throw the unchecked DbException. */
public final class Db {

    private Db() {}

    public interface Work { void run(Connection c) throws SQLException; }

    private static void bind(PreparedStatement ps, Object[] p) throws SQLException {
        for (int i = 0; i < p.length; i++) {
            Object v = p[i];
            if (v == null) ps.setObject(i + 1, null);
            else if (v instanceof LocalDate d) ps.setDate(i + 1, Date.valueOf(d));
            else if (v instanceof LocalTime t) ps.setTime(i + 1, Time.valueOf(t));
            else if (v instanceof LocalDateTime t) ps.setTimestamp(i + 1, Timestamp.valueOf(t));
            else ps.setObject(i + 1, v);
        }
    }

    private static List<Map<String, Object>> read(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        List<Map<String, Object>> out = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (int i = 1; i <= md.getColumnCount(); i++) {
                m.put(md.getColumnLabel(i), rs.getObject(i));
            }
            out.add(m);
        }
        return out;
    }

    // ---------- own connection ----------
    public static List<Map<String, Object>> rows(String sql, Object... p) {
        try (Connection c = DBConnection.getConnection()) {
            return rows(c, sql, p);
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public static Map<String, Object> row(String sql, Object... p) {
        List<Map<String, Object>> r = rows(sql, p);
        return r.isEmpty() ? null : r.get(0);
    }

    public static Object scalar(String sql, Object... p) {
        Map<String, Object> r = row(sql, p);
        return r == null ? null : r.values().iterator().next();
    }

    public static long num(String sql, Object... p) {
        Object o = scalar(sql, p);
        return o instanceof Number n ? n.longValue() : 0;
    }

    public static double dbl(String sql, Object... p) {
        Object o = scalar(sql, p);
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    public static int exec(String sql, Object... p) {
        try (Connection c = DBConnection.getConnection()) {
            return exec(c, sql, p);
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public static long insert(String sql, Object... p) {
        try (Connection c = DBConnection.getConnection()) {
            return insert(c, sql, p);
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    public static void tx(Work w) {
        try (Connection c = DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                w.run(c);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DbException(e);
        }
    }

    // ---------- given connection (inside tx) ----------
    public static List<Map<String, Object>> rows(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            try (ResultSet rs = ps.executeQuery()) {
                return read(rs);
            }
        }
    }

    public static Map<String, Object> row(Connection c, String sql, Object... p) throws SQLException {
        List<Map<String, Object>> r = rows(c, sql, p);
        return r.isEmpty() ? null : r.get(0);
    }

    public static int exec(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, p);
            return ps.executeUpdate();
        }
    }

    public static long insert(Connection c, String sql, Object... p) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, p);
            ps.executeUpdate();
            try (ResultSet k = ps.getGeneratedKeys()) {
                return k.next() ? k.getLong(1) : 0;
            }
        }
    }

    // ---------- simple dynamic CRUD ----------
    /** INSERT into table from column->value map. Returns generated id. */
    public static long insertMap(String table, Map<String, Object> v) {
        String cols = String.join(",", v.keySet());
        String qs = String.join(",", v.keySet().stream().map(k -> "?").toList());
        return insert("INSERT INTO " + table + " (" + cols + ") VALUES (" + qs + ")", v.values().toArray());
    }

    public static void updateMap(String table, String pk, Object id, Map<String, Object> v) {
        String set = String.join(",", v.keySet().stream().map(k -> k + "=?").toList());
        List<Object> args = new ArrayList<>(v.values());
        args.add(id);
        exec("UPDATE " + table + " SET " + set + " WHERE " + pk + "=?", args.toArray());
    }

    /** Next running code such as PRJ-001. */
    public static String nextCode(String table, String col, String prefix) {
        Object o = scalar("SELECT MAX(CAST(SUBSTRING_INDEX(" + col + ",'-',-1) AS UNSIGNED)) FROM " + table
                + " WHERE " + col + " LIKE ?", prefix + "-%");
        long n = o instanceof Number x ? x.longValue() : 0;
        return String.format("%s-%03d", prefix, n + 1);
    }

    /** Turns a JDBC error into a short readable message. */
    public static String friendly(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage() == null ? r.toString() : r.getMessage();
        if (m.startsWith("Duplicate entry")) return "This value already exists: " + m.replace("Duplicate entry ", "");
        if (m.contains("Check constraint")) return "A value is outside the allowed range.";
        if (m.contains("foreign key constraint")) return "This record is linked to other data and cannot be changed or removed.";
        if (m.contains("Communications link failure") || m.contains("Connection refused"))
            return LanguageManager.get("login.error.db");
        if (m.contains("Data too long")) return "A value is too long for its field.";
        return m;
    }
}
