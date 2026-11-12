package lk.technocom.pages;

import javafx.scene.Node;
import lk.technocom.service.EmailService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class StockPage {

    private StockPage() {}

    private static ListPage page;

    public static Node build() {
        page = new ListPage(Page.STOCK.text(), LanguageManager.x("Stock Item List - Add/Edit, Stock In, Stock Issue and Movement History open as pop-ups"))
                .sql("SELECT s.item_id, s.item_code, s.name, s.category, s.unit, s.quantity_on_hand, s.reorder_level, s.unit_price, "
                        + "s.supplier_id, CASE WHEN s.quantity_on_hand=0 THEN 'OUT' WHEN s.quantity_on_hand<=s.reorder_level THEN 'LOW' ELSE 'OK' END AS stock_status "
                        + "FROM stock_items s WHERE s.is_active=TRUE")
                .orderBy("t.item_code")
                .stat("Items", "SELECT COUNT(*) FROM stock_items WHERE is_active=TRUE")
                .stat("Low stock", "SELECT COUNT(*) FROM v_low_stock")
                .stat("Out of stock", "SELECT COUNT(*) FROM stock_items WHERE is_active=TRUE AND quantity_on_hand=0")
                .search("item_code", "name")
                .filterEnum("category", "Category", "CABLES", "PIPES", "FITTINGS", "TOOLS", "ELECTRICAL_ACCESSORIES")
                .filterEnum("stock_status", "Status", "OK", "LOW", "OUT")
                .col("item_code", "Code", K.TEXT, 0.7).col("name", "Item", K.TEXT, 1.6).col("category", "Category", K.NICE, 1)
                .col("unit", "Unit", K.NICE, 0.4).col("quantity_on_hand", "Qty", K.NUM, 0.6)
                .col("reorder_level", "Min", K.NUM, 0.6).col("stock_status", "Status", K.BADGE, 0.8)
                .act("In", r -> stockIn(r.get("item_id"), StockPage::reload))
                .act("Issue", r -> stockIssue(r.get("item_id"), StockPage::reload))
                .act("History", r -> history(r.get("item_id")))
                .top(UI.outline(LanguageManager.x("History"), () -> history(null)),
                        UI.outline(LanguageManager.x("Stock In"), () -> stockIn(null, StockPage::reload)),
                        UI.outline(LanguageManager.x("Stock Issue"), () -> stockIssue(null, StockPage::reload)))
                .crud(() -> addEdit(null, StockPage::reload), r -> addEdit(r, StockPage::reload),
                        r -> Crud.deactivate("stock_items", "item_id", r.get("item_id"), "Stock", String.valueOf(r.get("item_code"))));
        return page.build();
    }

    private static void reload() { page.reload(); }

    // ------------------------------------------------------------------ Add / Edit item
    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM stock_items WHERE item_id=?", row.get("item_id"));
        List<F> f = List.of(
                F.text("item_code", "Item code").req().def(add ? Db.nextCode("stock_items", "item_code", "ST") : null),
                F.text("name", "Item name").req(),
                F.en("category", "Category", "CABLES", "PIPES", "FITTINGS", "TOOLS", "ELECTRICAL_ACCESSORIES").req(),
                F.en("unit", "Unit", "M", "NOS", "KG", "SET").req(),
                add ? F.num("quantity_on_hand", "Opening quantity").def(0) : F.ro("quantity_on_hand", "Quantity on hand"),
                F.num("reorder_level", "Reorder level").req().def(0),
                F.num("unit_price", "Unit price (Rs)").req().def(0),
                F.lookup("supplier_id", "Supplier", Q.SUPPLIERS));
        Form.open(add ? LanguageManager.x("Add Item") : LanguageManager.x("Edit Item"), f, init, v -> {
            BigDecimal rl = (BigDecimal) v.get("reorder_level"), up = (BigDecimal) v.get("unit_price");
            if (rl.signum() < 0 || up.signum() < 0) return "Reorder level and price cannot be negative.";
            if (add) {
                BigDecimal q = v.get("quantity_on_hand") == null ? BigDecimal.ZERO : (BigDecimal) v.get("quantity_on_hand");
                if (q.signum() < 0) return "Opening quantity cannot be negative.";
                v.put("quantity_on_hand", q);
                Db.insertMap("stock_items", v);
                Audit.log("CREATE", "Stock", "Created item " + v.get("item_code"));
            } else {
                Db.updateMap("stock_items", "item_id", row.get("item_id"), v);
                Audit.log("UPDATE", "Stock", "Updated item " + v.get("item_code"));
            }
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Stock In
    public static void stockIn(Object itemId, Runnable done) {
        Map<String, Object> init = new java.util.LinkedHashMap<>();
        init.put("item_id", itemId);
        init.put("transaction_date", LocalDate.now());
        Form.open(LanguageManager.x("Stock In"), List.of(
                F.lookup("item_id", "Item", Q.STOCK_ITEMS).req().full(),
                F.num("quantity", "Quantity received").req(), F.date("transaction_date", "Date").req(),
                F.lookup("supplier_id", "Supplier", Q.SUPPLIERS), F.lookup("project_id", "Project (optional)", Q.PROJECTS),
                F.text("remarks", "Remarks / GRN no").full()), init, 620, v -> {
            BigDecimal q = (BigDecimal) v.get("quantity");
            if (q.signum() <= 0) return "Quantity must be above 0.";
            if (((LocalDate) v.get("transaction_date")).isAfter(LocalDate.now())) return "Date cannot be in the future.";
            Db.tx(c -> {
                Db.insert(c, "INSERT INTO stock_transactions (item_id,transaction_type,quantity,transaction_date,project_id,supplier_id,remarks,created_by) "
                                + "VALUES (?,?,?,?,?,?,?,?)", v.get("item_id"), "IN", q, v.get("transaction_date"), v.get("project_id"),
                        v.get("supplier_id"), v.get("remarks"), Session.getUserId());
                Db.exec(c, "UPDATE stock_items SET quantity_on_hand = quantity_on_hand + ? WHERE item_id=?", q, v.get("item_id"));
            });
            Audit.log("UPDATE", "Stock", "Stock in " + q + " for item id " + v.get("item_id"));
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Stock Issue
    public static void stockIssue(Object itemId, Runnable done) {
        Map<String, Object> init = new java.util.LinkedHashMap<>();
        init.put("item_id", itemId);
        init.put("transaction_date", LocalDate.now());
        Form.open(LanguageManager.x("Stock Issue"), List.of(
                F.lookup("item_id", "Item", Q.STOCK_ITEMS).req().full(),
                F.num("quantity", "Quantity to issue").req(), F.date("transaction_date", "Date").req(),
                F.lookup("project_id", "Project", Q.PROJECTS).req(), F.text("issued_to", "Issued to").req(),
                F.text("remarks", "Remarks").full()), init, 620, v -> {
            BigDecimal q = (BigDecimal) v.get("quantity");
            if (q.signum() <= 0) return "Quantity must be above 0.";
            if (((LocalDate) v.get("transaction_date")).isAfter(LocalDate.now())) return "Date cannot be in the future.";
            String[] err = {null};
            Db.tx(c -> {
                Map<String, Object> it = Db.row(c, "SELECT quantity_on_hand FROM stock_items WHERE item_id=? FOR UPDATE", v.get("item_id"));
                BigDecimal onHand = Fmt.dec(it.get("quantity_on_hand"));
                if (q.compareTo(onHand) > 0) {
                    err[0] = "Not enough stock. On hand: " + Fmt.num(onHand);
                    return;
                }
                Db.insert(c, "INSERT INTO stock_transactions (item_id,transaction_type,quantity,transaction_date,project_id,issued_to,remarks,created_by) "
                                + "VALUES (?,?,?,?,?,?,?,?)", v.get("item_id"), "ISSUE", q, v.get("transaction_date"), v.get("project_id"),
                        v.get("issued_to"), v.get("remarks"), Session.getUserId());
                Db.exec(c, "UPDATE stock_items SET quantity_on_hand = quantity_on_hand - ? WHERE item_id=?", q, v.get("item_id"));
            });
            if (err[0] != null) return err[0];
            Audit.log("UPDATE", "Stock", "Stock issue " + q + " for item id " + v.get("item_id"));
            checkLow(v.get("item_id"));
            return null;
        }, done);
    }

    /** After an issue: tell the keepers and managers if the item is now at/below its reorder level. */
    private static void checkLow(Object itemId) {
        Map<String, Object> it = Db.row("SELECT name, item_code, quantity_on_hand, reorder_level FROM stock_items WHERE item_id=?", itemId);
        if (it == null || Fmt.dec(it.get("quantity_on_hand")).compareTo(Fmt.dec(it.get("reorder_level"))) > 0) return;
        String msg = it.get("item_code") + " " + it.get("name") + " is low: " + Fmt.num(it.get("quantity_on_hand")) + " left (reorder at " + Fmt.num(it.get("reorder_level")) + ").";
        // Notifications must stay inside the signed-in organisation. The stock
        // catalogue is shared by the current schema, but a stock event must never
        // disclose another organisation's operational activity to its users.
        int organizationId = TenantScope.requireOrganization();
        List<Map<String, Object>> users = Db.rows("SELECT u.user_id, u.email FROM users u JOIN roles r ON r.role_id=u.role_id "
                + "WHERE u.organization_id=? AND u.status='ACTIVE' AND r.role_name IN ('Admin','Stock Keeper','Project Manager')",
                organizationId);
        for (Map<String, Object> u : users) {
            Db.exec("INSERT INTO notifications (user_id, category, message) VALUES (?, 'LOW_STOCK', ?)", u.get("user_id"), msg);
        }
        if ("true".equals(EmailService.setting("alert_low_stock", "true")) && EmailService.isConfigured()) {
            new Thread(() -> {
                for (Map<String, Object> u : users) EmailService.send(String.valueOf(u.get("email")), "Low stock: " + it.get("name"), msg, "LOW_STOCK");
            }).start();
        }
    }

    // ------------------------------------------------------------------ Stock history
    public static void history(Object itemId) {
        String where = itemId == null ? "" : " WHERE t.item_id=" + ((Number) itemId).longValue();
        ListPage h = new ListPage(LanguageManager.x("Stock History"), LanguageManager.x("Every stock in and issue"))
                .sql("SELECT t.transaction_id, t.transaction_date, i.item_code, i.name AS item, t.transaction_type, t.quantity, i.unit, "
                        + "COALESCE(CONCAT(p.project_code,' ',p.name),'-') AS project, COALESCE(s.name, t.issued_to, '-') AS party, "
                        + "COALESCE(t.remarks,'') AS remarks, u.full_name AS by_user FROM stock_transactions t "
                        + "JOIN stock_items i ON i.item_id=t.item_id LEFT JOIN projects p ON p.project_id=t.project_id "
                        + "LEFT JOIN suppliers s ON s.supplier_id=t.supplier_id JOIN users u ON u.user_id=t.created_by" + where)
                .orderBy("t.transaction_date DESC, t.transaction_id DESC")
                .search("item_code", "item", "project", "party", "remarks")
                .filterEnum("transaction_type", "Type", "IN", "ISSUE")
                .col("transaction_date", "Date", K.DATE, 0.8).col("item", "Item", K.TEXT, 1.4)
                .col("transaction_type", "Type", K.BADGE, 0.6).col("quantity", "Qty", K.NUM, 0.5)
                .col("unit", "Unit", K.NICE, 0.4).col("project", "Project", K.TEXT, 1.2)
                .col("party", "Supplier / Issued to", K.TEXT, 1.1).col("by_user", "By", K.TEXT, 0.9);
        Dialogs.show(LanguageManager.x("Stock History"), h.build(), 1100, 640);
    }
}
