package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.service.AutoMailer;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BoqPage {

    private BoqPage() {}

    private static ListPage page;

    private static final String ITEM_SUM = "(SELECT COALESCE(SUM(i.quantity*i.unit_price),0) FROM boq_items i WHERE i.boq_id=b.boq_id)";

    public static Node build() {
        page = new ListPage(Page.BOQ.text(), LanguageManager.x("Estimates and approval - BOQ Editor, Estimate and Approval open as pop-ups"))
                .sql("SELECT b.boq_id, b.boq_no, b.project_id, CONCAT(p.project_code,' ',p.name) AS project, b.version, "
                        + "b.contingency_percent, b.status, " + ITEM_SUM + " AS subtotal, "
                        + "ROUND(" + ITEM_SUM + " * (1 + b.contingency_percent/100), 2) AS total, b.created_by "
                        + "FROM boq b JOIN projects p ON p.project_id=b.project_id")
                .orderBy("t.boq_no DESC")
                .stat("Estimates", "SELECT COUNT(*) FROM boq")
                .stat("Pending approval", "SELECT COUNT(*) FROM boq WHERE status='SUBMITTED'")
                .statMoney("Approved value", "SELECT COALESCE(SUM(i.quantity*i.unit_price),0) FROM boq_items i JOIN boq b ON b.boq_id=i.boq_id WHERE b.status='APPROVED'")
                .search("boq_no", "project")
                .filterLookup("project_id", "Project", Q.PROJECTS)
                .filterEnum("status", "Status", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED")
                .col("boq_no", "BOQ", K.TEXT, 0.8).col("project", "Project", K.TEXT, 1.7)
                .col("version", "Ver.", K.NUM, 0.4).col("total", "Estimate total", K.MONEY, 1.1)
                .col("status", "Status", K.BADGE, 0.8)
                .act("Editor", r -> editor(r, BoqPage::reload))
                .act("Estimate", BoqPage::estimate)
                .actIf("Approve", r -> approval(r, BoqPage::reload), r -> "SUBMITTED".equals(r.get("status")) && Access.canApprove())
                .actIf("New version", BoqPage::newVersion, r -> "APPROVED".equals(r.get("status")) || "REJECTED".equals(r.get("status")))
                .crud(() -> addBoq(BoqPage::reload), r -> editor(r, BoqPage::reload), BoqPage::delete);
        return page.build();
    }

    private static void reload() { page.reload(); }

    private static void delete(Map<String, Object> r) {
        if (!editable(r.get("status"))) {
            Dialogs.error(LanguageManager.x("Only Draft or Rejected estimates can be deleted."));
            return;
        }
        Crud.deleteTree("boq", "boq_id", r.get("boq_id"), "BOQ", String.valueOf(r.get("boq_no")), "boq_items");
    }

    private static boolean editable(Object status) {
        return "DRAFT".equals(status) || "REJECTED".equals(status);
    }

    // ------------------------------------------------------------------ create
    private static void addBoq(Runnable done) {
        Form.open(LanguageManager.x("New BOQ"), List.of(
                F.lookup("project_id", "Project", Q.PROJECTS).req().full(),
                F.num("contingency_percent", "Contingency %").def(5).full()), null, 520, v -> {
            BigDecimal cp = (BigDecimal) v.get("contingency_percent");
            if (cp == null) cp = BigDecimal.ZERO;
            if (cp.signum() < 0 || cp.compareTo(BigDecimal.valueOf(100)) > 0) return "Contingency must be between 0 and 100.";
            long ver = Db.num("SELECT COALESCE(MAX(version),0)+1 FROM boq WHERE project_id=?", v.get("project_id"));
            Db.insert("INSERT INTO boq (boq_no, project_id, version, contingency_percent, created_by) VALUES (?,?,?,?,?)",
                    Db.nextCode("boq", "boq_no", "BOQ"), v.get("project_id"), ver, cp, Session.getUserId());
            Audit.log("CREATE", "BOQ", "Created BOQ for project id " + v.get("project_id"));
            return null;
        }, done);
    }

    private static void newVersion(Map<String, Object> r) {
        if (!Dialogs.confirm(LanguageManager.x("Create a new version of") + " " + r.get("boq_no") + "?")) return;
        Db.tx(c -> {
            long ver = ((Number) Db.row(c, "SELECT COALESCE(MAX(version),0)+1 AS v FROM boq WHERE project_id=?",
                    r.get("project_id")).get("v")).longValue();
            long id = Db.insert(c, "INSERT INTO boq (boq_no, project_id, version, contingency_percent, created_by) VALUES (?,?,?,?,?)",
                    Db.nextCode("boq", "boq_no", "BOQ"), r.get("project_id"), ver, r.get("contingency_percent"), Session.getUserId());
            Db.exec(c, "INSERT INTO boq_items (boq_id,item_no,description,unit,quantity,unit_price,actual_cost) "
                    + "SELECT ?,item_no,description,unit,quantity,unit_price,0 FROM boq_items WHERE boq_id=?", id, r.get("boq_id"));
        });
        Audit.log("CREATE", "BOQ", "New version from " + r.get("boq_no"));
        reload();
    }

    // ------------------------------------------------------------------ BOQ editor
    public static void editor(Map<String, Object> r, Runnable done) {
        Object id = r.get("boq_id");
        Map<String, Object> b = Db.row("SELECT * FROM boq WHERE boq_id=?", id);
        boolean edit = editable(b.get("status"));
        Label totals = UI.lbl("", "section-title");
        Label status = UI.badge(String.valueOf(b.get("status")));

        ListPage items = new ListPage(r.get("boq_no") + " - " + r.get("project"),
                LanguageManager.x("Bill of quantities items"))
                .sql("SELECT item_id, item_no, description, unit, quantity, unit_price, "
                        + "ROUND(quantity*unit_price,2) AS amount, actual_cost FROM boq_items WHERE boq_id=" + ((Number) id).longValue())
                .orderBy("t.item_no")
                .search("description")
                .col("item_no", "#", K.NUM, 0.3).col("description", "Description", K.TEXT, 2)
                .col("unit", "Unit", K.NICE, 0.5).col("quantity", "Qty", K.NUM, 0.6)
                .col("unit_price", "Unit price", K.MONEY, 0.9).col("amount", "Amount", K.MONEY, 1)
                .col("actual_cost", "Actual", K.MONEY, 0.9)
                .noExport();
        Runnable refresh = () -> {
            items.reload();
            Map<String, Object> t = Db.row("SELECT COALESCE(SUM(quantity*unit_price),0) AS s FROM boq_items WHERE boq_id=?", id);
            BigDecimal sub = Fmt.dec(t.get("s"));
            BigDecimal tot = sub.multiply(BigDecimal.ONE.add(Fmt.dec(b.get("contingency_percent")).divide(BigDecimal.valueOf(100))))
                    .setScale(2, RoundingMode.HALF_UP);
            totals.setText(LanguageManager.x("Subtotal") + " " + Fmt.money(sub) + "   |   "
                    + LanguageManager.x("Total with contingency") + " " + Fmt.money(tot));
        };
        if (edit) {
            items.act("Edit", it -> itemForm(id, it, refresh));
            items.act("Delete", it -> {
                if (Dialogs.confirm(LanguageManager.x("Delete this item?"))) {
                    Db.exec("DELETE FROM boq_items WHERE item_id=?", it.get("item_id"));
                    refresh.run();
                }
            });
        } else {
            items.act("Actual cost", it -> actualForm(it, refresh));
        }
        Stage[] st = new Stage[1];
        HBox bar = new HBox(10, status, totals, UI.grow());
        bar.setAlignment(Pos.CENTER_LEFT);
        if (edit) {
            bar.getChildren().add(UI.outline("+ " + LanguageManager.x("Add item"), () -> itemForm(id, null, refresh)));
            bar.getChildren().add(UI.primary(LanguageManager.x("Submit for approval"), () -> {
                if (Db.num("SELECT COUNT(*) FROM boq_items WHERE boq_id=?", id) == 0) {
                    Dialogs.error(LanguageManager.x("Add at least one item first."));
                    return;
                }
                Db.exec("UPDATE boq SET status='SUBMITTED', reject_reason=NULL WHERE boq_id=?", id);
                Audit.log("UPDATE", "BOQ", "Submitted " + r.get("boq_no"));
                st[0].close();
                if (done != null) done.run();
            }));
        }
        VBox body = new VBox(10, bar, items.build());
        body.setPadding(new Insets(10));
        VBox.setVgrow(body.getChildren().get(1), Priority.ALWAYS);
        refresh.run();
        st[0] = Dialogs.show(LanguageManager.x("BOQ Editor"), body, 1000, 640);
        st[0].setOnHidden(e -> {
            if (done != null) done.run();
        });
    }

    private static void itemForm(Object boqId, Map<String, Object> item, Runnable done) {
        long next = Db.num("SELECT COALESCE(MAX(item_no),0)+1 FROM boq_items WHERE boq_id=?", boqId);
        Form.open(item == null ? LanguageManager.x("Add item") : LanguageManager.x("Edit item"), List.of(
                F.integer("item_no", "Item no").req().def(next), F.en("unit", "Unit", "M", "NOS", "KG", "SET", "SQM").req(),
                F.text("description", "Description").req().full(),
                F.num("quantity", "Quantity").req(), F.num("unit_price", "Unit price (Rs)").req()),
                item == null ? null : Db.row("SELECT * FROM boq_items WHERE item_id=?", item.get("item_id")), 600, v -> {
                    if (((BigDecimal) v.get("quantity")).signum() <= 0) return "Quantity must be above 0.";
                    if (((BigDecimal) v.get("unit_price")).signum() < 0) return "Unit price cannot be negative.";
                    if (item == null) {
                        v.put("boq_id", boqId);
                        Db.insertMap("boq_items", v);
                    } else {
                        Db.updateMap("boq_items", "item_id", item.get("item_id"), v);
                    }
                    return null;
                }, done);
    }

    private static void actualForm(Map<String, Object> item, Runnable done) {
        Form.open(LanguageManager.x("Actual cost"), List.of(F.ro("description", "Description").full(),
                F.num("actual_cost", "Actual cost (Rs)").req().full()),
                Db.row("SELECT description, actual_cost FROM boq_items WHERE item_id=?", item.get("item_id")), 520, v -> {
                    if (((BigDecimal) v.get("actual_cost")).signum() < 0) return "Actual cost cannot be negative.";
                    Db.updateMap("boq_items", "item_id", item.get("item_id"), v);
                    return null;
                }, done);
    }

    // ------------------------------------------------------------------ Estimate calculation
    public static void estimate(Map<String, Object> r) {
        Map<String, Object> b = Db.row("SELECT b.*, p.project_code, p.name AS project FROM boq b "
                + "JOIN projects p ON p.project_id=b.project_id WHERE b.boq_id=?", r.get("boq_id"));
        List<Map<String, Object>> items = Db.rows("SELECT item_no, description, unit, quantity, unit_price, "
                + "quantity*unit_price AS amount, actual_cost FROM boq_items WHERE boq_id=? ORDER BY item_no", r.get("boq_id"));
        BigDecimal sub = BigDecimal.ZERO, actual = BigDecimal.ZERO;
        List<List<String>> rows = new ArrayList<>();
        for (Map<String, Object> i : items) {
            sub = sub.add(Fmt.dec(i.get("amount")));
            actual = actual.add(Fmt.dec(i.get("actual_cost")));
            rows.add(List.of(Fmt.str(i.get("item_no")), String.valueOf(i.get("description")), Fmt.nice(i.get("unit")),
                    Fmt.num(i.get("quantity")), Fmt.money(i.get("unit_price")), Fmt.money(i.get("amount")), Fmt.money(i.get("actual_cost"))));
        }
        BigDecimal cont = sub.multiply(Fmt.dec(b.get("contingency_percent"))).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal total = sub.add(cont);
        VBox sheet = Paper.sheet("Estimate " + b.get("boq_no") + " (v" + b.get("version") + ")", null);
        sheet.getChildren().add(Paper.kv(new String[][]{
                {"Project", b.get("project_code") + " " + b.get("project")}, {"Status", Fmt.nice(b.get("status"))}}));
        sheet.getChildren().add(Paper.table(List.of("#", "Description", "Unit", "Qty", "Unit price", "Amount", "Actual"), rows));
        sheet.getChildren().add(Paper.kv(new String[][]{
                {"Subtotal", Fmt.money(sub)}, {"Contingency (" + Fmt.num(b.get("contingency_percent")) + "%)", Fmt.money(cont)},
                {"ESTIMATE TOTAL", Fmt.money(total)}, {"Actual cost so far", Fmt.money(actual)},
                {"Variance (actual - estimate)", Fmt.money(actual.subtract(sub))}}));
        Paper.preview(LanguageManager.x("Estimate calculation"), sheet);
    }

    // ------------------------------------------------------------------ Estimate approval
    public static void approval(Map<String, Object> r, Runnable done) {
        Map<String, Object> b = Db.row("SELECT b.*, p.project_code, p.name AS project, u.full_name AS author, "
                + ITEM_SUM + " AS subtotal FROM boq b JOIN projects p ON p.project_id=b.project_id "
                + "JOIN users u ON u.user_id=b.created_by WHERE b.boq_id=?", r.get("boq_id"));
        BigDecimal total = Fmt.dec(b.get("subtotal")).multiply(BigDecimal.ONE.add(Fmt.dec(b.get("contingency_percent"))
                .divide(BigDecimal.valueOf(100)))).setScale(2, RoundingMode.HALF_UP);
        Stage[] st = new Stage[1];
        javafx.scene.control.TextArea reason = new javafx.scene.control.TextArea();
        reason.setPromptText(LanguageManager.x("Reason (required when rejecting)"));
        reason.setPrefRowCount(3);
        Label err = UI.lbl("", "error-text");
        VBox body = new VBox(14, Detail.grid("BOQ", b.get("boq_no"), "Project", b.get("project_code") + " " + b.get("project"),
                "Prepared by", b.get("author"), "Version", b.get("version"), "Estimate total", Fmt.money(total),
                "Status", Fmt.nice(b.get("status"))), reason, err,
                new HBox(10, UI.grow(), UI.outline(LanguageManager.x("View estimate"), () -> estimate(r)),
                        UI.danger(LanguageManager.x("Reject"), () -> {
                            if (reason.getText().isBlank()) {
                                err.setText(LanguageManager.x("Enter a reason to reject."));
                                return;
                            }
                            Db.exec("UPDATE boq SET status='REJECTED', reject_reason=?, approved_by=NULL, approved_at=NULL WHERE boq_id=?",
                                    reason.getText().trim(), b.get("boq_id"));
                            Db.exec("INSERT INTO notifications (user_id, category, message) VALUES (?, 'APPROVAL', ?)",
                                    b.get("created_by"), b.get("boq_no") + " was rejected: " + reason.getText().trim());
                            Audit.log("UPDATE", "BOQ", "Rejected " + b.get("boq_no"));
                            AutoMailer.toUser(b.get("created_by"), "APPROVAL", "Estimate " + b.get("boq_no") + " was rejected",
                                    "Reason: " + reason.getText().trim());
                            st[0].close();
                            if (done != null) done.run();
                        }),
                        UI.primary(LanguageManager.x("Approve"), () -> {
                            Db.exec("UPDATE boq SET status='APPROVED', approved_by=?, approved_at=NOW(), reject_reason=NULL WHERE boq_id=?",
                                    Session.getUserId(), b.get("boq_id"));
                            Db.exec("INSERT INTO notifications (user_id, category, message) VALUES (?, 'APPROVAL', ?)",
                                    b.get("created_by"), b.get("boq_no") + " was approved.");
                            Audit.log("UPDATE", "BOQ", "Approved " + b.get("boq_no"));
                            AutoMailer.toUser(b.get("created_by"), "APPROVAL", "Estimate " + b.get("boq_no") + " was approved",
                                    "Total: " + Fmt.money(total));
                            st[0].close();
                            if (done != null) done.run();
                        })));
        body.setPadding(new Insets(18, 22, 20, 22));
        st[0] = Dialogs.show(LanguageManager.x("Estimate Approval"), body, 640, 520);
    }
}
