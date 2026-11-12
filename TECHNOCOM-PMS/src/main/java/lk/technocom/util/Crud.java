package lk.technocom.util;

import java.util.Map;

/**
 * The 4 things almost every screen does: save, delete, hide (soft delete) and delete with children.
 * Table / column names are always written by us in code (never typed by users), so building the SQL here is safe.
 */
public final class Crud {

    private Crud() {}

    /** id == null -> INSERT, otherwise UPDATE. Writes the audit log. Returns the row id. */
    public static long save(String table, String pk, Object id, Map<String, Object> values, String module, String label) {
        if (id == null) {
            long newId = Db.insertMap(table, values);
            Audit.log("CREATE", module, "Created " + label);
            return newId;
        }
        Db.updateMap(table, pk, id, values);
        Audit.log("UPDATE", module, "Updated " + label);
        return ((Number) id).longValue();
    }

    /** Permanent delete of one row. */
    public static void delete(String table, String pk, Object id, String module, String label) {
        Db.exec("DELETE FROM " + table + " WHERE " + pk + "=?", id);
        Audit.log("DELETE", module, "Deleted " + label);
    }

    /** Hide a row instead of deleting it (the list pages only show is_active = TRUE). */
    public static void deactivate(String table, String pk, Object id, String module, String label) {
        Db.exec("UPDATE " + table + " SET is_active=FALSE WHERE " + pk + "=?", id);
        Audit.log("DELETE", module, "Removed " + label);
    }

    /** Delete the child rows first (they use the same key column name), then the parent, in one transaction. */
    public static void deleteTree(String table, String pk, Object id, String module, String label, String... childTables) {
        Db.tx(c -> {
            for (String child : childTables) Db.exec(c, "DELETE FROM " + child + " WHERE " + pk + "=?", id);
            Db.exec(c, "DELETE FROM " + table + " WHERE " + pk + "=?", id);
        });
        Audit.log("DELETE", module, "Deleted " + label);
    }
}
