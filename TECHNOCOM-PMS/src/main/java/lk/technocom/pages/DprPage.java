package lk.technocom.pages;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import lk.technocom.service.AutoMailer;
import lk.technocom.service.FileService;
import lk.technocom.ui.*;
import lk.technocom.ui.Form.F;
import lk.technocom.ui.ListPage.K;
import lk.technocom.util.*;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class DprPage {

    private DprPage() {}

    private static ListPage page;

    public static Node build() {
        page = new ListPage(Page.DPR.text(), LanguageManager.x("Daily Progress Reports - Add/Edit and View/Print open as pop-ups"))
                .sql("SELECT d.dpr_id, d.dpr_no, d.project_id, CONCAT(p.project_code,' ',p.name) AS project, d.report_date, "
                        + "d.weather, d.workers_count, d.progress_today, d.status, u.full_name AS author, d.created_by "
                        + "FROM daily_progress_reports d JOIN projects p ON p.project_id=d.project_id "
                        + "JOIN users u ON u.user_id=d.created_by")
                .orderBy("t.report_date DESC, t.dpr_no DESC")
                .stat("Reports", "SELECT COUNT(*) FROM daily_progress_reports")
                .stat("This week", "SELECT COUNT(*) FROM daily_progress_reports WHERE YEARWEEK(report_date,1)=YEARWEEK(CURDATE(),1)")
                .stat("Drafts", "SELECT COUNT(*) FROM daily_progress_reports WHERE status='DRAFT'")
                .search("dpr_no", "project", "author")
                .filterLookup("project_id", "Project", Q.PROJECTS)
                .filterEnum("weather", "Weather", "SUNNY", "CLOUDY", "RAINY")
                .filterEnum("status", "Status", "DRAFT", "SUBMITTED")
                .col("dpr_no", "DPR", K.TEXT, 0.8).col("project", "Project", K.TEXT, 1.6)
                .col("report_date", "Date", K.DATE, 0.9).col("weather", "Weather", K.NICE, 0.7)
                .col("workers_count", "Workers", K.NUM, 0.6).col("progress_today", "Progress %", K.NUM, 0.7)
                .col("status", "Status", K.BADGE, 0.8)
                .act("View", DprPage::view)
                .crud(() -> addEdit(null, DprPage::reload), DprPage::edit, DprPage::delete);
        return page.build();
    }

    private static void reload() { page.reload(); }

    private static boolean canEdit(Map<String, Object> r) {
        return "DRAFT".equals(r.get("status")) && (Access.canApprove()
                || String.valueOf(Session.getUserId()).equals(String.valueOf(r.get("created_by"))));
    }

    private static void edit(Map<String, Object> r) {
        if (!canEdit(r)) {
            Dialogs.error(LanguageManager.x("Only your own draft DPRs can be edited."));
            return;
        }
        addEdit(r, DprPage::reload);
    }

    private static void delete(Map<String, Object> r) {
        if (!canEdit(r)) {
            Dialogs.error(LanguageManager.x("Only your own draft DPRs can be deleted."));
            return;
        }
        Crud.delete("daily_progress_reports", "dpr_id", r.get("dpr_id"), "DPR", String.valueOf(r.get("dpr_no")));
    }

    // ------------------------------------------------------------------ Add / Edit DPR
    public static void addEdit(Map<String, Object> row, Runnable done) {
        boolean add = row == null;
        Map<String, Object> init = add ? null : Db.row("SELECT * FROM daily_progress_reports WHERE dpr_id=?", row.get("dpr_id"));
        String oldStatus = add ? "" : String.valueOf(init.get("status"));
        List<F> f = List.of(
                F.lookup("project_id", "Project", Q.PROJECTS).req(),
                F.date("report_date", "Report date").req().def(LocalDate.now()),
                F.en("weather", "Weather", "SUNNY", "CLOUDY", "RAINY").req(),
                F.integer("workers_count", "Workers on site").req().def(0),
                F.num("progress_today", "Progress today (%)").def(0),
                F.en("status", "Status", "DRAFT", "SUBMITTED").req().def("DRAFT"),
                F.area("work_completed", "Work completed").req(),
                F.area("issues", "Issues / delays"), F.area("materials_used", "Materials used"),
                F.file("photo", "Site photo (optional)").full());
        Form.open(add ? LanguageManager.x("Add DPR") : LanguageManager.x("Edit DPR"), f, init, v -> {
            if (((LocalDate) v.get("report_date")).isAfter(LocalDate.now())) return "Report date cannot be in the future.";
            int workers = (Integer) v.get("workers_count");
            if (workers < 0) return "Workers cannot be negative.";
            BigDecimal pt = v.get("progress_today") == null ? BigDecimal.ZERO : (BigDecimal) v.get("progress_today");
            if (pt.signum() < 0 || pt.compareTo(BigDecimal.valueOf(100)) > 0) return "Progress today must be between 0 and 100.";
            File photo = (File) v.remove("photo");
            String photoPath = null;
            if (photo != null) {
                try {
                    photoPath = FileService.store(photo, "dpr");
                } catch (IOException e) {
                    return "Could not save the photo: " + e.getMessage();
                }
            }
            final String pp = photoPath;
            final boolean newlySubmitted = "SUBMITTED".equals(v.get("status")) && !"SUBMITTED".equals(oldStatus);
            v.put("progress_today", pt);
            Db.tx(c -> {
                long id;
                if (add) {
                    String no = Db.nextCode("daily_progress_reports", "dpr_no", "DPR");
                    id = Db.insert(c, "INSERT INTO daily_progress_reports (dpr_no,project_id,report_date,weather,workers_count,"
                                    + "work_completed,progress_today,issues,materials_used,status,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                            no, v.get("project_id"), v.get("report_date"), v.get("weather"), v.get("workers_count"),
                            v.get("work_completed"), v.get("progress_today"), v.get("issues"), v.get("materials_used"),
                            v.get("status"), Session.getUserId());
                } else {
                    id = ((Number) row.get("dpr_id")).longValue();
                    Db.exec(c, "UPDATE daily_progress_reports SET project_id=?,report_date=?,weather=?,workers_count=?,"
                                    + "work_completed=?,progress_today=?,issues=?,materials_used=?,status=? WHERE dpr_id=?",
                            v.get("project_id"), v.get("report_date"), v.get("weather"), v.get("workers_count"),
                            v.get("work_completed"), v.get("progress_today"), v.get("issues"), v.get("materials_used"),
                            v.get("status"), id);
                }
                if (pp != null) Db.exec(c, "INSERT INTO dpr_photos (dpr_id, file_path) VALUES (?,?)", id, pp);
                if (newlySubmitted) {
                    Db.exec(c, "UPDATE projects SET progress = LEAST(100, progress + ROUND(?)) WHERE project_id=?",
                            pt, v.get("project_id"));
                }
            });
            if (newlySubmitted) AutoMailer.toManager(v.get("project_id"), "DPR", "DPR submitted for " + Fmt.str(v.get("report_date")),
                    "Workers: " + v.get("workers_count") + "\nProgress today: " + Fmt.num(pt) + " %\n\n" + v.get("work_completed"));
            if (newlySubmitted) AutoMailer.toClient(v.get("project_id"), "DPR", "Progress update for " + Fmt.str(v.get("report_date")),
                    "A new daily progress report was submitted for your project.\nProgress today: " + Fmt.num(pt)
                            + " %\n\nLog in to the Technocom client portal to see the full report.");
            Audit.log(add ? "CREATE" : "UPDATE", "DPR", (add ? "Created" : "Updated") + " DPR for project id " + v.get("project_id"));
            return null;
        }, done);
    }

    // ------------------------------------------------------------------ DPR View / Print
    public static void view(Map<String, Object> r) {
        Map<String, Object> d = Db.row("SELECT d.*, p.project_code, p.name AS project, c.name AS client, u.full_name AS author "
                + "FROM daily_progress_reports d JOIN projects p ON p.project_id=d.project_id "
                + "JOIN clients c ON c.client_id=p.client_id JOIN users u ON u.user_id=d.created_by WHERE d.dpr_id=?", r.get("dpr_id"));
        VBox sheet = Paper.sheet("Daily Progress Report - " + d.get("dpr_no"), null);
        sheet.getChildren().add(Paper.kv(new String[][]{
                {"Project", d.get("project_code") + " " + d.get("project")}, {"Client", String.valueOf(d.get("client"))},
                {"Report date", Fmt.str(d.get("report_date"))}, {"Weather", Fmt.nice(d.get("weather"))},
                {"Workers on site", Fmt.str(d.get("workers_count"))}, {"Progress today", Fmt.num(d.get("progress_today")) + " %"},
                {"Status", Fmt.nice(d.get("status"))}, {"Prepared by", String.valueOf(d.get("author"))}}));
        for (String[] s : new String[][]{{"work_completed", "Work completed"}, {"issues", "Issues / delays"}, {"materials_used", "Materials used"}}) {
            sheet.getChildren().add(UI.lbl(s[1].toUpperCase(), "paper-head"));
            sheet.getChildren().add(UI.lbl(d.get(s[0]) == null ? "-" : d.get(s[0]).toString(), "paper-cell"));
            ((javafx.scene.control.Label) sheet.getChildren().get(sheet.getChildren().size() - 1)).setWrapText(true);
        }
        List<Map<String, Object>> ph = Db.rows("SELECT file_path FROM dpr_photos WHERE dpr_id=?", d.get("dpr_id"));
        if (!ph.isEmpty()) {
            sheet.getChildren().add(UI.lbl(("Photos (" + ph.size() + ")").toUpperCase(), "paper-head"));
            HBox h = new HBox(10);
            for (Map<String, Object> p : ph) {
                File file = FileService.root().resolve(p.get("file_path").toString()).toFile();
                if (file.exists()) {
                    javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(
                            new javafx.scene.image.Image(file.toURI().toString(), 220, 160, true, true));
                    h.getChildren().add(iv);
                }
            }
            sheet.getChildren().add(h);
        }
        Paper.preview(LanguageManager.x("DPR View/Print"), sheet);
    }
}
