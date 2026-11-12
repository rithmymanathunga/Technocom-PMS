package lk.technocom.pages;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import lk.technocom.service.EmailService;
import lk.technocom.service.FileService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DrawingsPage {

    private DrawingsPage() {}

    private static ListPage page;
    private static final String[] AUTH = {"SLT", "CEB", "NWSDB", "CMC", "NSFD"};

    public static Node build() {
        page = new ListPage(Page.DRAWINGS.text(), LanguageManager.x("Drawing register - Add/Edit and Approval Tracker open as pop-ups"))
                .sql("SELECT d.drawing_id, d.drawing_no, d.project_id, CONCAT(p.project_code,' ',p.name) AS project, d.title, d.type, "
                        + "d.current_revision, d.status, d.remarks, "
                        + "CONCAT((SELECT COUNT(*) FROM drawing_approvals a WHERE a.drawing_id=d.drawing_id AND a.status='APPROVED'),' / 5') AS approvals "
                        + "FROM drawings d JOIN projects p ON p.project_id=d.project_id")
                .orderBy("t.drawing_no DESC")
                .stat("Drawings", "SELECT COUNT(*) FROM drawings")
                .stat("Approved", "SELECT COUNT(*) FROM drawings WHERE status='APPROVED'")
                .stat("Under review", "SELECT COUNT(*) FROM drawings WHERE status='SUBMITTED'")
                .stat("Rejected / Revise", "SELECT COUNT(*) FROM drawings WHERE status IN ('REJECTED','REVISE')")
                .search("drawing_no", "title", "project")
                .filterLookup("project_id", "Project", Q.PROJECTS)
                .filterEnum("type", "Type", "HVAC", "ELECTRICAL", "PLUMBING", "FIRE")
                .filterEnum("status", "Status", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "REVISE")
                .col("drawing_no", "Drawing", K.TEXT, 0.9).col("title", "Title", K.TEXT, 1.5)
                .col("project", "Project", K.TEXT, 1.4).col("type", "Type", K.NICE, 0.8)
                .col("current_revision", "Rev.", K.TEXT, 0.4).col("approvals", "Approvals", K.TEXT, 0.7)
                .col("status", "Status", K.BADGE, 0.8)
                .act("Tracker", r -> tracker(r, DrawingsPage::reload))
                .act("Approve", r -> clientDecision(r, "APPROVED"))
                .act("Request revision", r -> clientDecision(r, "REVISE"))
                .crud(() -> addEdit(null, DrawingsPage::reload), r -> addEdit(r, DrawingsPage::reload),
                        r -> Crud.deleteTree("drawings", "drawing_id", r.get("drawing_id"), "Drawings", String.valueOf(r.get("drawing_no")),
                                "drawing_approvals", "drawing_versions"));
        return page.build();
    }

    private static void reload() { page.reload(); }

    /** Use case "Manage drawing approvals" for the Client: approve a submitted drawing or ask for a revision. */
    private static void clientDecision(Map<String, Object> r, String status) {
        if (!"SUBMITTED".equals(String.valueOf(r.get("status")))) {
            Dialogs.info(Page.DRAWINGS.text(), LanguageManager.x("Only drawings that are submitted for approval can be decided."));
            return;
        }
        if (!Dialogs.confirm(LanguageManager.x("Confirm your decision for drawing") + " " + r.get("drawing_no") + ": " + Fmt.nice(status))) return;
        Db.exec("UPDATE drawings SET status=? WHERE drawing_id=?", status, r.get("drawing_id"));
        Db.exec("INSERT INTO audit_logs (user_id, action, module, details) VALUES (?, 'UPDATE', 'Drawings', ?)",
                Session.getUserId(), "Client decision " + status + " on " + r.get("drawing_no"));
        reload();                                   // the manager gets the e-mail from the auto mailer within minutes
    }

    // ------------------------------------------------------------------ Add / Edit drawing
    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM drawings WHERE drawing_id=?", row.get("drawing_id"));
        List<F> f = List.of(
                F.text("drawing_no", "Drawing no").req().def(add ? Db.nextCode("drawings", "drawing_no", "DRW") : null),
                F.lookup("project_id", "Project", Q.PROJECTS).req(),
                F.text("title", "Title").req().full(),
                F.en("type", "Type", "HVAC", "ELECTRICAL", "PLUMBING", "FIRE").req(),
                F.text("current_revision", "Revision").req().def("R0"),
                F.en("status", "Status", "DRAFT", "SUBMITTED", "APPROVED", "REJECTED", "REVISE").req().def("DRAFT"),
                F.text("remarks", "Remarks").full(),
                F.file("file", "Drawing file (PDF / DWG)").full());
        Form.open(add ? LanguageManager.x("Add Drawing") : LanguageManager.x("Edit Drawing"), f, init, v -> {
            File file = (File) v.remove("file");
            String path = null;
            if (file != null) {
                try {
                    path = FileService.store(file, "drawings");
                } catch (IOException e) {
                    return "Could not save the file: " + e.getMessage();
                }
            }
            final String fp = path;
            if (add) {
                Db.tx(c -> {
                    long id = Db.insert(c, "INSERT INTO drawings (drawing_no,project_id,title,type,current_revision,status,remarks) "
                                    + "VALUES (?,?,?,?,?,?,?)", v.get("drawing_no"), v.get("project_id"), v.get("title"), v.get("type"),
                            v.get("current_revision"), v.get("status"), v.get("remarks"));
                    for (String a : AUTH) Db.exec(c, "INSERT INTO drawing_approvals (drawing_id, authority) VALUES (?,?)", id, a);
                    if (fp != null) Db.exec(c, "INSERT INTO drawing_versions (drawing_id,revision,file_path,uploaded_by) VALUES (?,?,?,?)",
                            id, v.get("current_revision"), fp, Session.getUserId());
                });
                Audit.log("CREATE", "Drawings", "Created drawing " + v.get("drawing_no"));
            } else {
                Db.tx(c -> {
                    Db.exec(c, "UPDATE drawings SET drawing_no=?,project_id=?,title=?,type=?,current_revision=?,status=?,remarks=? WHERE drawing_id=?",
                            v.get("drawing_no"), v.get("project_id"), v.get("title"), v.get("type"), v.get("current_revision"),
                            v.get("status"), v.get("remarks"), row.get("drawing_id"));
                    if (fp != null) Db.exec(c, "INSERT INTO drawing_versions (drawing_id,revision,file_path,uploaded_by) VALUES (?,?,?,?)",
                            row.get("drawing_id"), v.get("current_revision"), fp, Session.getUserId());
                });
                Audit.log("UPDATE", "Drawings", "Updated drawing " + v.get("drawing_no"));
            }
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ Approval tracker
    public static void tracker(Map<String, Object> r, Runnable done) {
        Object id = r.get("drawing_id");
        VBox body = new VBox(12);
        Runnable[] fill = new Runnable[1];
        fill[0] = () -> {
            body.getChildren().clear();
            Map<String, Object> d = Db.row("SELECT d.*, CONCAT(p.project_code,' ',p.name) AS project FROM drawings d "
                    + "JOIN projects p ON p.project_id=d.project_id WHERE d.drawing_id=?", id);
            body.getChildren().add(Detail.grid("Drawing", d.get("drawing_no"), "Title", d.get("title"), "Project", d.get("project"),
                    "Type", Fmt.nice(d.get("type")), "Revision", d.get("current_revision"), "Overall status", Fmt.nice(d.get("status"))));
            body.getChildren().add(UI.lbl(LanguageManager.x("Authority approvals"), "section-title"));
            for (Map<String, Object> a : Db.rows("SELECT * FROM drawing_approvals WHERE drawing_id=? ORDER BY approval_id", id)) {
                VBox txt = new VBox(2, UI.lbl(String.valueOf(a.get("authority")), "kv-value"),
                        UI.muted(LanguageManager.x("Submitted") + ": " + Fmt.str(a.get("submitted_date")) + "   "
                                + LanguageManager.x("Decision") + ": " + Fmt.str(a.get("decision_date"))
                                + (a.get("comments") == null ? "" : "   -   " + a.get("comments"))));
                HBox h = UI.row(txt, UI.grow(), UI.badge(String.valueOf(a.get("status"))),
                        UI.link(LanguageManager.x("Update"), () -> authorityForm(d, a, fill[0])));
                VBox c = UI.card(h);
                c.setPadding(new Insets(10, 14, 10, 14));
                body.getChildren().add(c);
            }
            body.getChildren().add(UI.lbl(LanguageManager.x("File versions"), "section-title"));
            List<Map<String, Object>> vs = Db.rows("SELECT v.*, u.full_name FROM drawing_versions v JOIN users u ON u.user_id=v.uploaded_by "
                    + "WHERE v.drawing_id=? ORDER BY v.version_id DESC", id);
            if (vs.isEmpty()) body.getChildren().add(UI.muted(LanguageManager.x("No file uploaded yet")));
            for (Map<String, Object> v : vs) {
                body.getChildren().add(UI.row(UI.lbl(v.get("revision") + "  -  " + v.get("full_name") + "  " + Fmt.str(v.get("uploaded_at")), "kv-value"),
                        UI.grow(), UI.link(LanguageManager.x("Open"), () -> {
                            if (!FileService.open(String.valueOf(v.get("file_path"))))
                                Dialogs.error(LanguageManager.x("The file was not found on this computer."));
                        })));
            }
        };
        fill[0].run();
        Stage[] st = new Stage[1];
        VBox wrap = new VBox(12, body, new HBox(10, UI.grow(), UI.primary(LanguageManager.x("Close"), () -> st[0].close())));
        wrap.setPadding(new Insets(16, 20, 20, 20));
        st[0] = Dialogs.show(LanguageManager.x("Approval Tracker"), wrap, 760, 680);
        st[0].setOnHidden(e -> {
            if (done != null) done.run();
        });
    }

    private static void authorityForm(Map<String, Object> d, Map<String, Object> a, Runnable done) {
        Form.open(LanguageManager.x("Update") + " " + a.get("authority"), List.of(
                F.en("status", "Status", "PENDING", "SUBMITTED", "APPROVED", "REJECTED", "REVISE").req(),
                F.date("submitted_date", "Submitted date"), F.date("decision_date", "Decision date"),
                F.text("comments", "Comments").full()), a, 560, v -> {
            LocalDate sub = (LocalDate) v.get("submitted_date"), dec = (LocalDate) v.get("decision_date");
            String s = (String) v.get("status");
            if (sub != null && dec != null && dec.isBefore(sub)) return "Decision date cannot be before the submitted date.";
            if (!"PENDING".equals(s) && sub == null) v.put("submitted_date", LocalDate.now());
            if (("APPROVED".equals(s) || "REJECTED".equals(s) || "REVISE".equals(s)) && dec == null) v.put("decision_date", LocalDate.now());
            Db.updateMap("drawing_approvals", "approval_id", a.get("approval_id"), v);
            String overall = recompute(d.get("drawing_id"));
            Audit.log("UPDATE", "Drawings", d.get("drawing_no") + " " + a.get("authority") + " -> " + s);
            if ("APPROVED".equals(overall) || "REJECTED".equals(overall)) notifyManager(d, overall);
            return null;
        }, done);
    }

    /** Overall drawing status from the 5 authority decisions. */
    private static String recompute(Object drawingId) {
        List<Map<String, Object>> rows = Db.rows("SELECT status FROM drawing_approvals WHERE drawing_id=?", drawingId);
        boolean rej = false, rev = false, sub = false, all = !rows.isEmpty();
        for (Map<String, Object> r : rows) {
            String s = String.valueOf(r.get("status"));
            if ("REJECTED".equals(s)) rej = true;
            if ("REVISE".equals(s)) rev = true;
            if ("SUBMITTED".equals(s) || "APPROVED".equals(s)) sub = true;
            if (!"APPROVED".equals(s)) all = false;
        }
        String overall = rej ? "REJECTED" : rev ? "REVISE" : all ? "APPROVED" : sub ? "SUBMITTED" : "DRAFT";
        Db.exec("UPDATE drawings SET status=? WHERE drawing_id=?", overall, drawingId);
        return overall;
    }

    private static void notifyManager(Map<String, Object> d, String overall) {
        Map<String, Object> m = Db.row("SELECT u.user_id, u.email FROM projects p JOIN users u ON u.user_id=p.manager_id WHERE p.project_id=?",
                d.get("project_id"));
        if (m == null) return;
        String msg = "Drawing " + d.get("drawing_no") + " is now " + overall + ".";
        Db.exec("INSERT INTO notifications (user_id, category, message) VALUES (?, 'APPROVAL', ?)", m.get("user_id"), msg);
        if ("true".equals(EmailService.setting("alert_drawing_approval", "true")) && EmailService.isConfigured()) {
            String to = String.valueOf(m.get("email"));
            new Thread(() -> EmailService.send(to, "Drawing " + d.get("drawing_no") + " " + overall, msg, "DRAWING_APPROVAL")).start();
        }
    }
}
